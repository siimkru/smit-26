package ee.smit.agent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

    @Test
    void retainsOnlyFourMostRecentExchanges() {
        SessionStore store = new SessionStore();
        for (int index = 0; index < 6; index++) {
            store.remember("session", new AgentExchange("q" + index, "a" + index));
        }

        assertThat(store.history("session"))
                .extracting(AgentExchange::question)
                .containsExactly("q2", "q3", "q4", "q5");
    }

    @Test
    void keepsSessionsIsolatedAndDoesNotPersistAnonymousTurns() {
        SessionStore store = new SessionStore();
        store.remember("session-a", new AgentExchange("a-question", "a-answer"));
        store.remember("session-b", new AgentExchange("b-question", "b-answer"));
        store.remember(null, new AgentExchange("anonymous-question", "anonymous-answer"));

        assertThat(store.history("session-a")).extracting(AgentExchange::question)
                .containsExactly("a-question");
        assertThat(store.history("session-b")).extracting(AgentExchange::question)
                .containsExactly("b-question");
        assertThat(store.history(null)).isEmpty();
        assertThat(store.history("missing")).isEmpty();
    }
}
