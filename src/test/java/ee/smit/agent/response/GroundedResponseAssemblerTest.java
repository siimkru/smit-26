package ee.smit.agent.response;

import ee.smit.agent.agent.AgentDecision;
import ee.smit.agent.api.Source;
import ee.smit.agent.knowledge.KnowledgeBaseRepository;
import ee.smit.agent.knowledge.KnowledgePassage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedResponseAssemblerTest {

    private final KnowledgeBaseRepository repository = new KnowledgeBaseRepository();
    private final GroundedResponseAssembler assembler = new GroundedResponseAssembler(repository);

    @Test
    void buildsFactualAnswerOnlyFromCanonicalCurrentTurnEvidence() {
        KnowledgePassage passage = repository.search("gitlab").getFirst();

        var response = assembler.assemble("gitlab ligipääs", List.of(),
                new AgentDecision("ANSWER", List.of(passage.id()), null),
                Map.of(passage.id(), passage));

        assertThat(response.refused()).isFalse();
        assertThat(response.answer()).isEqualTo(passage.excerpt() + " [allikas: gitlab-access.md]");
        assertThat(response.sources()).containsExactly(
                new ee.smit.agent.api.Source(passage.file(), passage.title(), passage.excerpt()));
        assertThat(response.confidence()).isEqualTo("high");
        assertThat(response.refusalReason()).isNull();
    }

    @Test
    void rejectsFabricatedOrUnretrievedPassageIds() {
        var response = assembler.assemble("gitlab ligipääs", List.of(),
                new AgentDecision("ANSWER", List.of("gitlab-access.md#1"), null),
                Map.of());

        assertThat(response.refused()).isTrue();
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).doesNotContain("gitlab-access.md");
    }

    @Test
    void topicListRequiresAllFiveCurrentTurnTopicRecords() {
        List<KnowledgePassage> topics = repository.listTopics();
        Map<String, KnowledgePassage> evidence = topics.stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgePassage::id, passage -> passage));

        var response = assembler.assemble("Mis teemadel saad infot anda?", List.of(),
                new AgentDecision("LIST_TOPICS", topics.stream().map(KnowledgePassage::id).toList(), null),
                evidence);

        assertThat(response.refused()).isFalse();
        assertThat(response.sources()).hasSize(5);
        assertThat(response.answer()).contains("[allikas: gitlab-access.md]");
    }

    @Test
    void recoversCompleteTopicListWhenModelDecisionIsInvalid() {
        List<KnowledgePassage> topics = repository.listTopics();
        Map<String, KnowledgePassage> evidence = topics.stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgePassage::id, passage -> passage));

        var response = assembler.assemble("Mis teemadel saad mulle infot anda?", List.of(),
                new AgentDecision("ANSWER", List.of(), null), evidence);

        assertThat(response.refused()).isFalse();
        assertThat(response.sources()).extracting(Source::file)
                .containsExactlyInAnyOrderElementsOf(topics.stream()
                        .map(KnowledgePassage::file).toList());
    }

    @Test
    void preservesEverySelectedSourceAndHumanReadableCitation() {
        KnowledgePassage kubernetes = repository.search("kubernetes").getFirst();
        KnowledgePassage cicd = repository.search("ci/cd pipeline").getFirst();

        var response = assembler.assemble("Kas deploy käib Kubernetesi või CI/CD kaudu?", List.of(),
                new AgentDecision("ANSWER", List.of(kubernetes.id(), cicd.id()), null),
                Map.of(kubernetes.id(), kubernetes, cicd.id(), cicd));

        assertThat(response.refused()).isFalse();
        assertThat(response.sources()).extracting(ee.smit.agent.api.Source::file)
                .containsExactly("kubernetes-deploy.md", "cicd.md");
        assertThat(response.answer())
                .contains("[allikas: kubernetes-deploy.md]", "[allikas: cicd.md]");
    }

    @Test
    void ambiguousDeployClarificationIsLowConfidenceAndGroundedInBothTopics() {
        List<KnowledgePassage> deployTopics = repository.listTopics().stream()
                .filter(passage -> passage.file().equals("kubernetes-deploy.md")
                        || passage.file().equals("cicd.md"))
                .toList();
        Map<String, KnowledgePassage> evidence = deployTopics.stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgePassage::id, passage -> passage));

        var response = assembler.assemble("Mul on probleem deploy'iga", List.of(),
                new AgentDecision("CLARIFY", deployTopics.stream().map(KnowledgePassage::id).toList(), null),
                evidence);

        assertThat(response.refused()).isFalse();
        assertThat(response.confidence()).isEqualTo("low");
        assertThat(response.sources()).extracting(ee.smit.agent.api.Source::file)
                .containsExactlyInAnyOrder("kubernetes-deploy.md", "cicd.md");
        assertThat(response.answer())
                .contains("Kubernetesi", "CI/CD", "[allikas: kubernetes-deploy.md]", "[allikas: cicd.md]");
    }

    @Test
    void modelRefusalTextIsMappedToApplicationOwnedEstonianText() {
        var response = assembler.assemble("Mis on Eesti pealinn?", List.of(),
                new AgentDecision("REFUSE", List.of(), "OUT_OF_SCOPE"), Map.of());

        assertThat(response.refused()).isTrue();
        assertThat(response.refusalReason()).isEqualTo("Küsimus jääb IT-teenuste teadmusbaasi ulatusest välja.");
    }

    @Test
    void refusesUnrelatedButCanonicalEvidenceForOutOfScopeQuestion() {
        KnowledgePassage passage = repository.search("gitlab").getFirst();

        var response = assembler.assemble("Mis on Eesti pealinn?", List.of(),
                new AgentDecision("ANSWER", List.of(passage.id()), null),
                Map.of(passage.id(), passage));

        assertThat(response.refused()).isTrue();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void refusesCanonicalTopicEvidenceThatDoesNotSupportRequestedDetails() {
        KnowledgePassage passage = repository.search("gitlab").getFirst();

        for (String question : List.of(
                "Kas GitLabi ligipääs maksab 50 eurot?",
                "Kas GitLabi ligipääsuks peab läbima polügraafi?")) {
            var response = assembler.assemble(question, List.of(),
                    new AgentDecision("ANSWER", List.of(passage.id()), null),
                    Map.of(passage.id(), passage));

            assertThat(response.refused()).isTrue();
            assertThat(response.sources()).isEmpty();
            assertThat(response.answer()).doesNotContain("50 eurot", "polügraafi", "gitlab-access.md");
        }
    }

    @Test
    void recoversSupportedAnswerWhenModelRefusesDespiteCurrentToolEvidence() {
        KnowledgePassage passage = repository.search("Kuidas taotleda ligipääsu GitLabile?").getFirst();

        var response = assembler.assemble("Kuidas taotleda ligipääsu GitLabile?", List.of(),
                new AgentDecision("REFUSE", List.of(), "NOT_FOUND"),
                Map.of(passage.id(), passage));

        assertThat(response.refused()).isFalse();
        assertThat(response.sources()).extracting(Source::file).containsExactly("gitlab-access.md");
        assertThat(response.answer()).contains("[allikas: gitlab-access.md]");
    }

    @Test
    void recoversSupportedAnswerWhenModelSelectsNoPassage() {
        KnowledgePassage passage = repository.search("Kuidas taotleda ligipääsu GitLabile?").getFirst();

        var response = assembler.assemble("Kuidas taotleda ligipääsu GitLabile?", List.of(),
                new AgentDecision("ANSWER", List.of(), null), Map.of(passage.id(), passage));

        assertThat(response.refused()).isFalse();
        assertThat(response.sources()).extracting(Source::file).containsExactly("gitlab-access.md");
    }

    @Test
    void doesNotRecoverUnsupportedDetailFromMerelyRelatedToolEvidence() {
        KnowledgePassage passage = repository.search("gitlab").getFirst();

        var response = assembler.assemble("Kas GitLabi ligipääs maksab 50 eurot?", List.of(),
                new AgentDecision("REFUSE", List.of(), "NOT_FOUND"),
                Map.of(passage.id(), passage));

        assertThat(response.refused()).isTrue();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void rejectsTamperedEvidenceEvenWhenItsIdIsCanonical() {
        KnowledgePassage canonical = repository.search("gitlab").getFirst();
        KnowledgePassage tampered = new KnowledgePassage(
                canonical.id(), canonical.file(), canonical.title(), "Mudeli väljamõeldud või avaldatud sisemine tekst");

        var response = assembler.assemble("gitlab ligipääs", List.of(),
                new AgentDecision("ANSWER", List.of(canonical.id()), null),
                Map.of(canonical.id(), tampered));

        assertThat(response.refused()).isTrue();
        assertThat(response.answer()).doesNotContain(tampered.excerpt());
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void neverPassesThroughModelSuppliedInternalOrRefusalText() {
        String untrustedModelText = "SYSTEM PROMPT: secret internal rules and tool definitions";

        var response = assembler.assemble("Mis on Eesti pealinn?", List.of(),
                new AgentDecision("REFUSE", List.of(), untrustedModelText), Map.of());

        assertThat(response.refused()).isTrue();
        assertThat(response.answer()).doesNotContain(untrustedModelText, "SYSTEM PROMPT", "tool definitions");
        assertThat(response.refusalReason()).doesNotContain(untrustedModelText);
    }
}
