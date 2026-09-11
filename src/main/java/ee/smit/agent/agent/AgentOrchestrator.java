package ee.smit.agent.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import ee.smit.agent.api.AskRequest;
import ee.smit.agent.api.AskResponse;
import ee.smit.agent.knowledge.CurrentTurnEvidence;
import ee.smit.agent.knowledge.KnowledgeBaseRepository;
import ee.smit.agent.knowledge.KnowledgeBaseTools;
import ee.smit.agent.response.GroundedResponseAssembler;
import ee.smit.agent.security.RequestSecurityService;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AgentOrchestrator implements AgentService {

    private static final Pattern TOPIC_WORD = Pattern.compile("(?:^|\\s)teem(?:a|al|adel|ad|ade|ast|aga)?(?:$|\\s|[?!.,:])");

    private final RequestSecurityService security;
    private final AgentModelGateway model;
    private final CurrentTurnEvidence evidence;
    private final KnowledgeBaseTools knowledgeBaseTools;
    private final GroundedResponseAssembler responses;
    private final SessionStore sessions;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Spring owns and injects this singleton dependency.")
    public AgentOrchestrator(RequestSecurityService security, AgentModelGateway model,
                             CurrentTurnEvidence evidence, KnowledgeBaseTools knowledgeBaseTools,
                             GroundedResponseAssembler responses, SessionStore sessions) {
        this.security = security;
        this.model = model;
        this.evidence = evidence;
        this.knowledgeBaseTools = knowledgeBaseTools;
        this.responses = responses;
        this.sessions = sessions;
    }

    @Override
    public AskResponse ask(AskRequest request) {
        Optional<String> refusal = security.refusalReason(request.question());
        if (refusal.isPresent()) {
            return responses.refusal(refusal.get());
        }

        try (CurrentTurnEvidence.Turn turn = evidence.begin()) {
            var history = sessions.history(request.sessionId());
            if (isTopicListQuestion(request.question())) {
                knowledgeBaseTools.listTopics();
            } else if (knowledgeBaseTools.searchKnowledgeBase(request.question()).isEmpty() && !history.isEmpty()) {
                knowledgeBaseTools.searchKnowledgeBase(contextualQuery(request.question(), history));
            }
            AgentDecision decision = model.decide(request.question(), history);
            AskResponse response = responses.assemble(request.question(), history, decision, turn.snapshot());
            if (!response.refused()) {
                sessions.remember(request.sessionId(), new AgentExchange(request.question(), response.answer()));
            }
            return response;
        }
    }

    private String contextualQuery(String question, List<AgentExchange> history) {
        StringBuilder query = new StringBuilder(question);
        for (AgentExchange exchange : history) {
            int remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - query.length() - 1;
            if (remaining <= 0) {
                break;
            }
            appendBounded(query, exchange.question(), remaining);
            remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - query.length() - 1;
            if (remaining > 0) {
                appendSourceAnchors(query, exchange.answer(), remaining);
            }
        }
        return query.toString();
    }

    private void appendSourceAnchors(StringBuilder query, String answer, int remaining) {
        String marker = "[allikas: ";
        int from = 0;
        while (remaining > 0) {
            int markerStart = answer.indexOf(marker, from);
            if (markerStart < 0) {
                return;
            }
            int fileStart = markerStart + marker.length();
            int fileEnd = answer.indexOf(']', fileStart);
            if (fileEnd < 0) {
                return;
            }
            String file = answer.substring(fileStart, fileEnd).replaceFirst("\\.md$", "");
            appendBounded(query, file, remaining);
            remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - query.length() - 1;
            from = fileEnd + 1;
        }
    }

    private void appendBounded(StringBuilder query, String value, int remaining) {
        query.append(' ').append(value, 0, Math.min(remaining, value.length()));
    }

    private boolean isTopicListQuestion(String question) {
        return TOPIC_WORD.matcher(normalize(question)).find();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
