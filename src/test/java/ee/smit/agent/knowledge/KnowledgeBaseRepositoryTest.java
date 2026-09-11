package ee.smit.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThat(repository.search("Kas GitLabi ligipääs luuakse 9 tööpäeva jooksul?")).isEmpty();
        assertThat(repository.search(
                "Kuidas taotleda GitLabi ligipääsu ja kas selleks peab läbima polügraafi?")).isEmpty();
    }

    @Test
    void retainsSupportedNumericDetailsAsEvidence() {
        assertThat(repository.search("Kas GitLabi ligipääs luuakse 1–2 tööpäeva jooksul?"))
                .extracting(KnowledgePassage::file).containsExactly("gitlab-access.md");
    }

    @Test
    void acceptsCuratedNaturalParaphrases() {
        assertThat(repository.search("Kuidas GitLabi juurdepääsu saada?"))
                .extracting(KnowledgePassage::file).contains("gitlab-access.md");
        assertThat(repository.search("Kuidas kontrollida koodi enne ühendamist?"))
                .extracting(KnowledgePassage::file).contains("code-review.md");
        assertThat(repository.search("Kuidas Kubernetes kasutusele võtta?"))
                .extracting(KnowledgePassage::file).contains("kubernetes-deploy.md");
        assertThat(repository.search("How do I get GitLab access? Vajaksin juhiseid."))
                .extracting(KnowledgePassage::file).contains("gitlab-access.md");
        assertThat(repository.search("Kust see info pärineb? Kuidas taotleda ligipääsu GitLabile?"))
                .extracting(KnowledgePassage::file).contains("gitlab-access.md");
    }

    @ParameterizedTest(name = "supported paraphrase remains grounded: {0}")
    @ValueSource(strings = {
            "Kuidas GitLabile ligi pääseda?",
            "Milline on GitLabi juurdepääsu taotlemise kord?",
            "Palun juhenda mind GitLabi ligipääsu saamisel",
            "Mida teha, et enne ühendamist kood üle vaadata?",
            "Kuidas Kuberneteses juurutamine käib?"
    })
    void acceptsBroaderCuratedParaphrases(String question) {
        assertThat(repository.search(question)).isNotEmpty();
    }

    @Test
    void acceptsQuestionsAboutTimingAndHowToStart() {
        assertThat(repository.search("Kui kiiresti saan GitLabi ligipääsu?"))
                .extracting(KnowledgePassage::file).containsExactly("gitlab-access.md");
        assertThat(repository.search("Soovin GitLabi ligipääsu, kuidas alustada?"))
                .extracting(KnowledgePassage::file).containsExactly("gitlab-access.md");
        assertThat(repository.search("Kes kinnitab GitLabi taotluse?"))
                .extracting(KnowledgePassage::file).containsExactly("gitlab-access.md");
    }

    @Test
    void acceptsCommonGitlabAccessParaphrases() {
        assertThat(repository.search("Kuidas pääsen GitLabi?"))
                .extracting(KnowledgePassage::file).containsExactly("gitlab-access.md");
        assertThat(repository.search("Kelle heakskiitu on GitLabi ligipääsuks vaja?"))
                .extracting(KnowledgePassage::file).containsExactly("gitlab-access.md");
    }

    @Test
    void doesNotUseSubstringCollisionsAsKnowledgeEvidence() {
        assertThat(repository.search("Kuidas GitLabine töövoog töötab?")).isEmpty();
        assertThat(repository.search("Mis on Kuberneteslik platvorm?")).isEmpty();
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
