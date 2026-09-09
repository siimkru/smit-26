package ee.smit.agent.agent;

import java.util.List;

public interface AgentModelGateway {
    AgentDecision decide(String question, List<AgentExchange> history);
}
