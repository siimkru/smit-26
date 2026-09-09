package ee.smit.agent.agent;

import ee.smit.agent.api.AskRequest;
import ee.smit.agent.api.AskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Temporary API boundary until the knowledge-base agent is implemented. */
@Service
public class AgentNotImplementedService implements AgentService {

    @Override
    public AskResponse ask(AskRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Teadmusbaasi agent ei ole veel rakendatud.");
    }
}
