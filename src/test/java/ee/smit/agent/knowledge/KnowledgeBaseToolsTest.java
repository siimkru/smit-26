package ee.smit.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBaseToolsTest {

    private final KnowledgeBaseTools tools = new KnowledgeBaseTools(new KnowledgeBaseRepository());
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
        ToolCallbackProvider provider = new KnowledgeBaseToolConfiguration()
                .knowledgeBaseToolCallbackProvider(tools, allowlist);

        assertThat(Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrderElementsOf(allowlist.allowedNames());
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
    }

    @Test
    void topicListingExposesOnlyCuratedKnowledgeBaseRecords() {
        assertThat(tools.listTopics())
                .allSatisfy(passage -> {
                    assertThat(passage.file()).doesNotContain("/").doesNotContain("\\");
                    assertThat(passage.excerpt()).isNotBlank();
                });
    }
}
