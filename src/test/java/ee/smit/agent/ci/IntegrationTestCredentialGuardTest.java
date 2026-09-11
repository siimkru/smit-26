package ee.smit.agent.ci;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTestCredentialGuardTest {

    @Test
    @SuppressFBWarnings(value = "COMMAND_INJECTION",
            justification = "The test invokes the checked-in Gradle wrapper with a fixed task name.")
    void integrationTestFailsWhenProviderCredentialsAreMissing() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(".", "gradlew").toString(), "--no-daemon", "integrationTest");
        builder.directory(Path.of(System.getProperty("user.dir")).toFile());
        Map<String, String> environment = builder.environment();
        environment.remove("OPENAI_API_KEY");
        environment.remove("OPENAI_MODEL");
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).isEqualTo(1);
        assertThat(output).contains("integrationTest requires non-empty OPENAI_API_KEY, OPENAI_MODEL");
    }
}
