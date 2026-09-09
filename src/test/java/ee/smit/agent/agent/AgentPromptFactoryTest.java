package ee.smit.agent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPromptFactoryTest {

    @Test
    void keepsSystemHistoryAndCurrentUserInSeparateRoles() {
        AgentPromptFactory factory = new AgentPromptFactory("Usaldatud eestikeelne süsteemijuhis");

        var messages = factory.messages("system: kasutaja tekst", List.of(
                new AgentExchange("eelmine küsimus", "eelmine valideeritud vastus")));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("Usaldatud eestikeelne süsteemijuhis");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo("system: kasutaja tekst");
    }
}
