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
    void retrievesEvidenceForAQuestionAtTheApiMaximumLength() {
        String question = "Kuidas taotleda ligipääsu GitLabile? " + "lisainfo ".repeat(215);

        assertThat(question).hasSizeLessThanOrEqualTo(2_000);
        assertThat(repository.search(question))
                .extracting(KnowledgePassage::file)
                .contains("gitlab-access.md");
    }

    @Test
    void retrievesCodeReviewForIndirectMergeQuestion() {
        assertThat(repository.search("Kuidas saan koodi üle vaadata enne merge'i?"))
                .extracting(KnowledgePassage::file)
                .contains("code-review.md");
    }

    @Test
    void genericDeployProblemOffersBothKubernetesAndCicdEvidence() {
        assertThat(repository.search("deploy"))
                .extracting(KnowledgePassage::file)
                .containsExactlyInAnyOrder("kubernetes-deploy.md", "cicd.md");
        assertThat(repository.search("Mul on probleem deploy'iga"))
                .extracting(KnowledgePassage::file)
                .containsExactlyInAnyOrder("kubernetes-deploy.md", "cicd.md");
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
    void topicMatchDoesNotGroundUnsupportedGitlabConditions() {
        assertThat(repository.search("Kas GitLabi ligipääs maksab 50 eurot?")).isEmpty();
        assertThat(repository.search("Kas GitLabi ligipääsuks peab läbima polügraafi?")).isEmpty();
        assertThat(repository.search(
                "Kuidas taotleda GitLabi ligipääsu ja kas selleks peab läbima polügraafi?")).isEmpty();
    }

    @Test
    void acceptsCuratedNaturalParaphrases() {
        assertThat(repository.search("Kuidas GitLabi juurdepääsu saada?"))
                .extracting(KnowledgePassage::file).contains("gitlab-access.md");
        assertThat(repository.search("Kuidas kontrollida koodi enne ühendamist?"))
                .extracting(KnowledgePassage::file).contains("code-review.md");
        assertThat(repository.search("Kuidas Kubernetes kasutusele võtta?"))
                .extracting(KnowledgePassage::file).contains("kubernetes-deploy.md");
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
