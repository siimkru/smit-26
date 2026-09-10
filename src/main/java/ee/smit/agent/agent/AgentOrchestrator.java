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
import java.util.Locale;
import java.util.Optional;

@Service
public class AgentOrchestrator implements AgentService {

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
                knowledgeBaseTools.searchKnowledgeBase(contextualQuery(
                        request.question(), history.getLast().question()));
            }
            AgentDecision decision = model.decide(request.question(), history);
            AskResponse response = responses.assemble(request.question(), history, decision, turn.snapshot());
            if (!response.refused()) {
                sessions.remember(request.sessionId(), new AgentExchange(request.question(), response.answer()));
            }
            return response;
        }
    }

    private String contextualQuery(String question, String previousQuestion) {
        int remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - question.length() - 1;
        if (remaining <= 0) {
            return question.substring(0, KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH);
        }
        return question + " " + previousQuestion.substring(0, Math.min(remaining, previousQuestion.length()));
    }

    private boolean isTopicListQuestion(String question) {
        return normalize(question).contains("teem");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
