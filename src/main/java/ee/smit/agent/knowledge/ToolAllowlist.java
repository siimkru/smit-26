package ee.smit.agent.knowledge;

import org.springframework.stereotype.Component;

import java.util.Set;

/** The complete set of functions that may be handed to an AI model. */
@Component
public class ToolAllowlist {

    private static final Set<String> ALLOWED_NAMES = Set.of("listTopics", "searchKnowledgeBase");

    public Set<String> allowedNames() {
        return ALLOWED_NAMES;
    }

    public boolean isAllowed(String toolName) {
        return ALLOWED_NAMES.contains(toolName);
    }

    public void requireAllowed(String toolName) {
        if (!isAllowed(toolName)) {
            throw new IllegalArgumentException("Lubamatu teadmusriba tööriist: " + toolName);
        }
    }
}
