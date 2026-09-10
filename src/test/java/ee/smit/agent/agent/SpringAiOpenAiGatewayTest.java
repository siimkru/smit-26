package ee.smit.agent.agent;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiOpenAiGatewayTest {

    private final SpringAiOpenAiGateway gateway = new SpringAiOpenAiGateway(
            new OpenAiAgentProperties("", "", 0.2),
            new AgentPromptFactory("trusted system prompt"),
            ToolCallbackProvider.from(),
            new ObjectMapper());

    @Test
    void parsesOnlyTheClosedDecisionContract() {
        AgentDecision decision = gateway.parseDecision("""
                {"action":"ANSWER","selectedPassageIds":["gitlab-access.md#1"],"refusalReason":null}
                """);

        assertThat(decision).isEqualTo(
                new AgentDecision("ANSWER", List.of("gitlab-access.md#1"), null));
    }

    @Test
    void rejectsModelProseFieldsAndTrailingDisclosureText() {
        AgentDecision decisionWithPublicProse = gateway.parseDecision("""
                {"action":"ANSWER","selectedPassageIds":["gitlab-access.md#1"],
                 "refusalReason":null,"answer":"untrusted model prose"}
                """);
        AgentDecision decisionWithTrailingText = gateway.parseDecision("""
                {"action":"REFUSE","selectedPassageIds":[],"refusalReason":"UNSAFE"}
                leaked internal instructions
                """);

        assertThat(decisionWithPublicProse.action()).isNull();
        assertThat(decisionWithTrailingText.action()).isNull();
    }

    @Test
    void missingCredentialsFailLocallyWithoutAttemptingAnOpenAiCall() {
        assertThatThrownBy(() -> gateway.decide("gitlab ligipääs?", List.of()))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessage("OpenAI konfiguratsioon puudub.");
    }
}
