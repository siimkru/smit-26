package ee.smit.agent.api;

import ee.smit.agent.agent.AgentService;
import ee.smit.agent.agent.ModelUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AgentController.class, HealthController.class})
@Import(ApiExceptionHandler.class)
class AgentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentService agentService;

    @Test
    @DisplayName("valid ask request preserves the public response DTO contract")
    void delegatesValidRequestAndSerializesResponse() throws Exception {
        when(agentService.ask(new AskRequest("Kuidas taotleda ligipääsu GitLabile?", "session-1")))
                .thenReturn(new AskResponse(
                        "Näidisvastus [allikas: gitlab-access.md]",
                        java.util.List.of(new Source("gitlab-access.md", "GitLab", "Näidislõik")),
                        "high",
                        false,
                        null));

        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Kuidas taotleda ligipääsu GitLabile?\",\"sessionId\":\"session-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Näidisvastus [allikas: gitlab-access.md]"))
                .andExpect(jsonPath("$.sources[0].file").value("gitlab-access.md"))
                .andExpect(jsonPath("$.sources[0].excerpt").value("Näidislõik"))
                .andExpect(jsonPath("$.confidence").value("high"))
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.refusalReason").value(nullValue()));
    }

    @Test
    @DisplayName("API-01 - empty question returns 400")
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("API-02 - missing question returns 400")
    void rejectsMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("SEC-07 - overlong question returns 400 before agent invocation")
    void rejectsOverlongQuestion() throws Exception {
        String question = "x".repeat(3001);

        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("API-03 - health check returns 200 without agent invocation")
    void returnsHealthWithoutAgentInvocation() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("provider unavailability returns a sanitized 503")
    void returnsSanitizedServiceUnavailable() throws Exception {
        AskRequest request = new AskRequest("Kuidas taotleda ligipääsu GitLabile?", null);
        when(agentService.ask(request)).thenThrow(new ModelUnavailableException("secret provider detail"));

        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Kuidas taotleda ligipääsu GitLabile?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("OpenAI teenus ei ole praegu saadaval."));
    }
}
