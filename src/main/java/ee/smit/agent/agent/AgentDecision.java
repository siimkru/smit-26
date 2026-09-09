package ee.smit.agent.agent;

import java.util.List;

/** Untrusted model output. Public answer text never comes from this record. */
public record AgentDecision(String action, List<String> selectedPassageIds, String refusalReason) {
    public AgentDecision {
        selectedPassageIds = selectedPassageIds == null ? List.of() : List.copyOf(selectedPassageIds);
    }
}
