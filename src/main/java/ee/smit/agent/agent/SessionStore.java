package ee.smit.agent.agent;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small, bounded, in-memory context store. Session content is context, never evidence. */
@Component
public class SessionStore {

    static final int MAX_EXCHANGES = 4;
    static final int MAX_SESSIONS = 1_000;
    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private final Map<String, Session> sessions = new LinkedHashMap<>(16, 0.75f, true);
    private final LongSupplier nanoTime;

    public SessionStore() {
        this(System::nanoTime);
    }

    SessionStore(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public synchronized List<AgentExchange> history(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        long now = nanoTime.getAsLong();
        removeExpired(now);
        Session session = sessions.get(sessionId);
        if (session == null) {
            return List.of();
        }
        session.lastAccessNanos = now;
        return List.copyOf(new ArrayList<>(session.exchanges));
    }

    public synchronized void remember(String sessionId, AgentExchange exchange) {
        if (sessionId == null) {
            return;
        }
        long now = nanoTime.getAsLong();
        removeExpired(now);
        Session session = sessions.computeIfAbsent(sessionId, ignored -> new Session(now));
        session.lastAccessNanos = now;
        session.exchanges.addLast(exchange);
        while (session.exchanges.size() > MAX_EXCHANGES) {
            session.exchanges.removeFirst();
        }
        while (sessions.size() > MAX_SESSIONS) {
            sessions.remove(sessions.keySet().iterator().next());
        }
    }

    private void removeExpired(long now) {
        sessions.values().removeIf(session -> now - session.lastAccessNanos >= IDLE_TIMEOUT.toNanos());
    }

    private static final class Session {
        private final ArrayDeque<AgentExchange> exchanges = new ArrayDeque<>();
        private long lastAccessNanos;

        private Session(long lastAccessNanos) {
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
