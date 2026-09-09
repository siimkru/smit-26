package ee.smit.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "agent.openai.api-key=",
        "agent.openai.model="
})
class SmitAgentApplicationTest {

    @Test
    void contextStartsWithoutOpenAiCredentials() {
    }
}
