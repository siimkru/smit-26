package ee.smit.agent;

import ee.smit.agent.agent.OpenAiAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenAiAgentProperties.class)
public class SmitAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmitAgentApplication.class, args);
    }
}
