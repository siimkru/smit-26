package ee.smit.agent.knowledge;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/** Registers precisely the two allowlisted annotated tools for later agent use. */
@Configuration
class KnowledgeBaseToolConfiguration {

    @Bean
    ToolCallbackProvider knowledgeBaseToolCallbackProvider(KnowledgeBaseTools tools, ToolAllowlist allowlist) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build()
                .getToolCallbacks();

        if (callbacks.length != allowlist.allowedNames().size()
                || Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()).anyMatch(name -> !allowlist.isAllowed(name))) {
            throw new IllegalStateException("Registreeritud tööriistad ei vasta lubatud nimekirjale.");
        }
        return ToolCallbackProvider.from(callbacks);
    }
}
