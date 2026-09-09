package ee.smit.agent.agent;

import ee.smit.agent.api.AskRequest;
import ee.smit.agent.api.AskResponse;
import ee.smit.agent.knowledge.CurrentTurnEvidence;
import ee.smit.agent.response.GroundedResponseAssembler;
import ee.smit.agent.security.RequestSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AgentOrchestrator implements AgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final RequestSecurityService security;
    private final AgentModelGateway model;
    private final CurrentTurnEvidence evidence;
    private final GroundedResponseAssembler responses;
    private final SessionStore sessions;

    public AgentOrchestrator(RequestSecurityService security, AgentModelGateway model,
                             CurrentTurnEvidence evidence, GroundedResponseAssembler responses,
                             SessionStore sessions) {
        this.security = security;
        this.model = model;
        this.evidence = evidence;
        this.responses = responses;
        this.sessions = sessions;
    }

    @Override
    public AskResponse ask(AskRequest request) {
        Optional<String> refusal = security.refusalReason(request.question());
        if (refusal.isPresent()) {
            LOGGER.warn("Agent request refused before model call; questionLength={}", request.question().length());
            return responses.refusal(refusal.get());
        }

        try (CurrentTurnEvidence.Turn turn = evidence.begin()) {
            var history = sessions.history(request.sessionId());
            AgentDecision decision = model.decide(request.question(), history);
            AskResponse response = responses.assemble(request.question(), history, decision, turn.snapshot());
            if (!response.refused()) {
                sessions.remember(request.sessionId(), new AgentExchange(request.question(), response.answer()));
            }
            return response;
        }
    }
}
