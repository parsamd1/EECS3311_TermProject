package avsa.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application configuration. Values come from a local, git-ignored {@code .env}
 * file and from environment variables; an environment variable overrides the
 * file. Secrets are never hard-coded and never printed in full.
 */
public final class AppConfig {

    public static final String API_KEY = "ANTHROPIC_API_KEY";
    public static final String MODEL = "LLM_MODEL";
    public static final String DEFAULT_MODEL = "claude-haiku-4-5";

    /** Value shipped in .env.example; treated as "no key configured". */
    static final String PLACEHOLDER_KEY = "YOUR_LOCAL_API_KEY_HERE";

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** Loads {@code .env} from the working directory, then the process environment. */
    public static AppConfig load() {
        return load(Path.of(".env"), System.getenv());
    }

    public static AppConfig load(Path envFile, Map<String, String> environment) {
        Map<String, String> merged = new HashMap<>();
        if (Files.isRegularFile(envFile)) {
            try {
                merged.putAll(parseEnvFile(Files.readAllLines(envFile, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read configuration file " + envFile, e);
            }
        }
        for (String key : List.of(API_KEY, MODEL)) {
            String value = environment.get(key);
            if (value != null && !value.isBlank()) {
                merged.put(key, value.strip());
            }
        }
        return new AppConfig(merged);
    }

    /** Parses {@code KEY=VALUE} lines; blank lines, comments and lines without '=' are skipped. */
    static Map<String, String> parseEnvFile(List<String> lines) {
        Map<String, String> result = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = unquote(line.substring(eq + 1).strip());
            result.put(key, value);
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /** The LLM API key, or empty when it is missing, blank, or still the example placeholder. */
    public Optional<String> llmApiKey() {
        String key = values.get(API_KEY);
        if (key == null || key.isBlank() || key.equals(PLACEHOLDER_KEY)) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    public String llmModelId() {
        String model = values.get(MODEL);
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    /** A display-safe form of the key: only its last four characters are shown. */
    public String maskedApiKey() {
        return llmApiKey()
                .map(k -> k.length() <= 4 ? "****" : "****" + k.substring(k.length() - 4))
                .orElse("not set");
    }
}
