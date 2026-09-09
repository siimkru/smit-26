package ee.smit.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring AI boundary. Only the explicitly supplied KB callbacks are visible to OpenAI. */
@Component
public class SpringAiOpenAiGateway implements AgentModelGateway {

    private final AgentPromptFactory promptFactory;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiOpenAiGateway(OpenAiAgentProperties properties,
                                 AgentPromptFactory promptFactory,
                                 ToolCallbackProvider knowledgeBaseToolCallbackProvider,
                                 ObjectMapper objectMapper) {
        this.promptFactory = promptFactory;
        this.chatClient = createClient(properties, knowledgeBaseToolCallbackProvider);
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentDecision decide(String question, List<AgentExchange> history) {
        if (chatClient == null) {
            throw new ModelUnavailableException("OpenAI konfiguratsioon puudub.");
        }
        try {
            String content = chatClient.prompt(new Prompt(promptFactory.messages(question, history)))
                    .call()
                    .content();
            return parseDecision(content);
        } catch (ModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelUnavailableException("OpenAI päring ebaõnnestus.", exception);
        }
    }

    AgentDecision parseDecision(String content) {
        if (content == null || content.isBlank()) {
            return new AgentDecision(null, List.of(), null);
        }
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return objectMapper.readerFor(AgentDecision.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(json);
        } catch (JsonProcessingException exception) {
            return new AgentDecision(null, List.of(), null);
        }
    }

    private ChatClient createClient(OpenAiAgentProperties properties, ToolCallbackProvider tools) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.model() == null || properties.model().isBlank()) {
            return null;
        }

        OpenAiApi api = OpenAiApi.builder().apiKey(properties.apiKey()).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.model())
                .temperature(properties.temperature())
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(model)
                .defaultToolCallbacks(tools)
                .build();
    }
}
