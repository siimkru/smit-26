package ee.smit.agent.response;

import ee.smit.agent.agent.AgentDecision;
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
}
