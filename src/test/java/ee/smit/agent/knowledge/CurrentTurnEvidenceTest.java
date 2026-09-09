package ee.smit.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentTurnEvidenceTest {

    @Test
    void recordsOnlyEvidenceFromTheActiveTurnAndClearsItOnClose() {
        CurrentTurnEvidence evidence = new CurrentTurnEvidence();
        KnowledgePassage passage = new KnowledgePassage(
                "gitlab-access.md#1", "gitlab-access.md", "GitLabi ligipääs", "Kanooniline lõik");

        try (CurrentTurnEvidence.Turn turn = evidence.begin()) {
            evidence.record(List.of(passage));
            assertThat(turn.snapshot()).containsEntry(passage.id(), passage);
            assertThatThrownBy(evidence::begin).isInstanceOf(IllegalStateException.class);
        }

        try (CurrentTurnEvidence.Turn nextTurn = evidence.begin()) {
            assertThat(nextTurn.snapshot()).isEmpty();
        }
    }
}
