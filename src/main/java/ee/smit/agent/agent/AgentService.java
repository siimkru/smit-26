package ee.smit.agent.agent;

import ee.smit.agent.api.AskRequest;
import ee.smit.agent.api.AskResponse;

public interface AgentService {
    AskResponse ask(AskRequest request);
}
