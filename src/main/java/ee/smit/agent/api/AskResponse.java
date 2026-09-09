package ee.smit.agent.api;

import java.util.List;

public record AskResponse(
        String answer,
        List<Source> sources,
        String confidence,
        boolean refused,
        String refusalReason
) {
}
