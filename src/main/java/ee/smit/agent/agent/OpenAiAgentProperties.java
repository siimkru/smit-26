package ee.smit.agent.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.openai")
public record OpenAiAgentProperties(String apiKey, String model, double temperature) {
}
