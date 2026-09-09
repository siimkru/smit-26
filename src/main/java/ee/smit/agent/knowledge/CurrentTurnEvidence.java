package ee.smit.agent.knowledge;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request-local record of canonical passages actually returned by an allowlisted tool. */
@Component
public class CurrentTurnEvidence {

    private final ThreadLocal<LinkedHashMap<String, KnowledgePassage>> passages = new ThreadLocal<>();

    public Turn begin() {
        if (passages.get() != null) {
            throw new IllegalStateException("Tõendite kogumine on juba aktiivne.");
        }
        passages.set(new LinkedHashMap<>());
        return new Turn();
    }

    public void record(List<KnowledgePassage> returnedPassages) {
        LinkedHashMap<String, KnowledgePassage> current = passages.get();
        if (current != null) {
            returnedPassages.forEach(passage -> current.putIfAbsent(passage.id(), passage));
        }
    }

    public final class Turn implements AutoCloseable {
        private boolean closed;

        public Map<String, KnowledgePassage> snapshot() {
            if (closed) {
                throw new IllegalStateException("Tõendite kogumine on lõpetatud.");
            }
            return Map.copyOf(passages.get());
        }

        @Override
        public void close() {
            if (!closed) {
                passages.remove();
                closed = true;
            }
        }
    }
}
