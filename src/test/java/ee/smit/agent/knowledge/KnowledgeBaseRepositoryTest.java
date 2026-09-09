package ee.smit.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseRepositoryTest {

    private final KnowledgeBaseRepository repository = new KnowledgeBaseRepository();

    @Test
    void listsExactlyTheFiveCuratedTopics() {
        assertThat(repository.listTopics())
                .extracting(KnowledgePassage::file)
                .containsExactly("gitlab-access.md", "kubernetes-deploy.md", "cicd.md", "code-review.md", "access-management.md");
    }

    @Test
    void retrievesGitlabForEstonianAccessQuestion() {
        List<KnowledgePassage> results = repository.search("Kuidas taotleda ligipääsu GitLabile?");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().file()).isEqualTo("gitlab-access.md");
        assertThat(results.getFirst().excerpt()).contains("Ligipääsutaotlus");
    }

    @Test
    void retrievesCodeReviewForIndirectMergeQuestion() {
        assertThat(repository.search("Kuidas saan koodi üle vaadata enne merge'i?"))
                .extracting(KnowledgePassage::file)
                .contains("code-review.md");
    }

    @Test
    void returnsNoResultForUnknownTokens() {
        assertThat(repository.search("Marsi kvantvõtme orbitaaljaam")).isEmpty();
    }

    @Test
    void genericAccessWordDoesNotGroundAnUnknownNamedTarget() {
        assertThat(repository.search("Kuidas taotleda ligipääsu Marsi serverile?")).isEmpty();
    }

    @Test
    void allowsCicdTermWithoutTreatingItsSlashAsAFilePath() {
        assertThat(repository.search("Kuidas CI/CD pipeline töötab?"))
                .extracting(KnowledgePassage::file)
                .contains("cicd.md");
    }

    @Test
    void rejectsAbsoluteTraversalAndOversizedSearchArguments() {
        assertThat(repository.search("/etc/passwd")).isEmpty();
        assertThat(repository.search("/tmp/gitlab")).isEmpty();
        assertThat(repository.search("C:\\Windows\\System32\\drivers\\etc\\hosts")).isEmpty();
        assertThat(repository.search("../knowledge-base/gitlab-access.md")).isEmpty();
        assertThat(repository.search("x".repeat(KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH + 1))).isEmpty();
    }
}
