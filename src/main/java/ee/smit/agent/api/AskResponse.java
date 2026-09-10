package ee.smit.agent.api;

import java.util.List;

public record AskResponse(
        String answer,
        List<Source> sources,
        String confidence,
        boolean refused,
        String refusalReason
) {
    public AskResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @Override
    public List<Source> sources() {
        return List.copyOf(sources);
    }
}
