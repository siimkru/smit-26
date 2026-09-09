package ee.smit.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "question on kohustuslik")
        @Size(max = 2000, message = "question võib olla kuni 2000 tähemärki")
        String question,
        String sessionId
) {
}
