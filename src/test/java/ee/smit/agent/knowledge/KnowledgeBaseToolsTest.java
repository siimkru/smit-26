package ee.smit.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBaseToolsTest {

    private final KnowledgeBaseTools tools = new KnowledgeBaseTools(
            new KnowledgeBaseRepository(), new CurrentTurnEvidence());
    private final ToolAllowlist allowlist = new ToolAllowlist();

    @Test
    void exposesOnlyTheTwoExplicitSpringAiTools() {
        Set<String> annotatedNames = Arrays.stream(KnowledgeBaseTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .map(Tool::name)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(annotatedNames).containsExactlyInAnyOrderElementsOf(allowlist.allowedNames());
    }

    @Test
    void springAiCallbackProviderRegistersOnlyAllowlistedTools() {
        ToolCallbackProvider provider = callbackProvider();

        assertThat(Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrderElementsOf(allowlist.allowedNames());
    }

    @Test
    void registeredSearchCallbackUsesTheReadOnlyRepositoryForArguments() {
        ToolCallback callback = Arrays.stream(callbackProvider().getToolCallbacks())
                .filter(value -> value.getToolDefinition().name().equals("searchKnowledgeBase"))
                .findFirst()
                .orElseThrow();

        assertThat(callback.call("{\"query\":\"gitlab ligipääs\"}"))
                .contains("gitlab-access.md");
        assertThat(callback.call("{\"query\":\"../../../etc/passwd\"}"))
                .doesNotContain("root:x:", "/etc/passwd");
    }

    @Test
    void rejectsAnyToolOutsideTheAllowlist() {
        assertThatThrownBy(() -> allowlist.requireAllowed("readFile"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(allowlist.isAllowed("searchKnowledgeBase")).isTrue();
    }

    @Test
    void traversalLikeSearchInputCannotReadArbitraryFiles() {
        assertThat(tools.searchKnowledgeBase("../../../etc/passwd")).isEmpty();
        assertThat(tools.searchKnowledgeBase("..\\..\\secrets.txt")).isEmpty();
        assertThat(tools.searchKnowledgeBase("/Users/example/.ssh/id_rsa")).isEmpty();
        assertThat(tools.searchKnowledgeBase("/tmp/gitlab")).isEmpty();
        assertThat(tools.searchKnowledgeBase("x".repeat(KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH + 1)))
                .isEmpty();
    }

    @Test
    void topicListingExposesOnlyCuratedKnowledgeBaseRecords() {
        assertThat(tools.listTopics())
                .allSatisfy(passage -> {
                    assertThat(passage.file()).doesNotContain("/").doesNotContain("\\");
                    assertThat(passage.excerpt()).isNotBlank();
                });
    }

    private ToolCallbackProvider callbackProvider() {
        return new KnowledgeBaseToolConfiguration()
                .knowledgeBaseToolCallbackProvider(tools, allowlist);
    }
}
