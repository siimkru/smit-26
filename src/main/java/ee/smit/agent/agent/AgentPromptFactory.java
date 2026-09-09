package ee.smit.agent.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class AgentPromptFactory {

    private final String systemPrompt;

    @Autowired
    public AgentPromptFactory(@Value("classpath:prompts/agent-system.txt") Resource resource) {
        try {
            this.systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Agendi süsteemiprompti ei saanud laadida.", exception);
        }
    }

    AgentPromptFactory(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public List<Message> messages(String question, List<AgentExchange> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (AgentExchange exchange : history) {
            messages.add(new UserMessage(exchange.question()));
            messages.add(new AssistantMessage(exchange.answer()));
        }
        messages.add(new UserMessage(question));
        return List.copyOf(messages);
    }
}
