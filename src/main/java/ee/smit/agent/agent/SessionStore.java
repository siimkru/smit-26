package ee.smit.agent.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded, in-memory context store. Session content is context, never evidence. */
@Component
public class SessionStore {

    static final int MAX_EXCHANGES = 4;
    static final int MAX_SESSIONS = 1_000;
    private final Map<String, ArrayDeque<AgentExchange>> sessions = new LinkedHashMap<>(16, 0.75f, true);

    public synchronized List<AgentExchange> history(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        ArrayDeque<AgentExchange> exchanges = sessions.get(sessionId);
        if (exchanges == null) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(exchanges));
    }

    public synchronized void remember(String sessionId, AgentExchange exchange) {
        if (sessionId == null) {
            return;
        }
        ArrayDeque<AgentExchange> exchanges = sessions.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        exchanges.addLast(exchange);
        while (exchanges.size() > MAX_EXCHANGES) {
            exchanges.removeFirst();
        }
        while (sessions.size() > MAX_SESSIONS) {
            sessions.remove(sessions.keySet().iterator().next());
        }
    }
}
