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
}
