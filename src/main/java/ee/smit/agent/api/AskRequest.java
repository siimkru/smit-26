package ee.smit.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "question on kohustuslik")
        @Size(max = 2000, message = "question võib olla kuni 2000 tähemärki")
        String question,
        @Size(max = 128, message = "sessionId võib olla kuni 128 tähemärki")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "sessionId sisaldab lubamatuid märke")
        String sessionId
) {
}
