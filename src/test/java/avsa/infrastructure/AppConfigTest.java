package avsa.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

    @TempDir
    Path dir;

    private Path envFile(String... lines) throws IOException {
        return Files.write(dir.resolve(".env"), List.of(lines));
    }

    @Test
    void readsKeyAndModelFromEnvFile() throws IOException {
        Path env = envFile("ANTHROPIC_API_KEY=sk-test-1234", "LLM_MODEL=claude-haiku-4-5");
        AppConfig config = AppConfig.load(env, Map.of());
        assertEquals("sk-test-1234", config.llmApiKey().orElseThrow());
        assertEquals("claude-haiku-4-5", config.llmModelId());
    }

    @Test
    void skipsCommentsBlankLinesAndExportPrefixAndStripsQuotes() {
        Map<String, String> parsed = AppConfig.parseEnvFile(List.of(
                "# a comment",
                "",
                "export ANTHROPIC_API_KEY=\"sk-quoted\"",
                "LLM_MODEL='claude-haiku-4-5'",
                "not a key value line"));
        assertEquals(Map.of("ANTHROPIC_API_KEY", "sk-quoted", "LLM_MODEL", "claude-haiku-4-5"), parsed);
    }

    @Test
    void environmentVariableOverridesEnvFile() throws IOException {
        Path env = envFile("ANTHROPIC_API_KEY=from-file");
        AppConfig config = AppConfig.load(env, Map.of("ANTHROPIC_API_KEY", "from-environment"));
        assertEquals("from-environment", config.llmApiKey().orElseThrow());
    }

    @Test
    void missingFileGivesNoKeyAndDefaultModel() {
        AppConfig config = AppConfig.load(dir.resolve("does-not-exist"), Map.of());
        assertFalse(config.llmApiKey().isPresent());
        assertEquals(AppConfig.DEFAULT_MODEL, config.llmModelId());
        assertEquals("not set", config.maskedApiKey());
    }

    @Test
    void examplePlaceholderCountsAsNoKey() throws IOException {
        Path env = envFile("ANTHROPIC_API_KEY=" + AppConfig.PLACEHOLDER_KEY);
        assertFalse(AppConfig.load(env, Map.of()).llmApiKey().isPresent());
    }

    @Test
    void maskedKeyShowsOnlyLastFourCharacters() throws IOException {
        Path env = envFile("ANTHROPIC_API_KEY=sk-secret-value-9876");
        String masked = AppConfig.load(env, Map.of()).maskedApiKey();
        assertEquals("****9876", masked);
        assertTrue(!masked.contains("secret"));
    }
}
