package avsa.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.infrastructure.AppConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvsaCliTest {

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;
    private AvsaCli cli;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        cli = new AvsaCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                          new PrintStream(err, true, StandardCharsets.UTF_8),
                          () -> AppConfig.load(Path.of("no-such-file"),
                                  Map.of(AppConfig.API_KEY, "sk-test-secret-4321")));
    }

    @Test
    void helpPrintsUsageAndSucceeds() {
        assertEquals(AvsaCli.EXIT_OK, cli.run(new String[] {"--help"}));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage: avsa"));
    }

    @Test
    void noArgumentsPrintsUsage() {
        assertEquals(AvsaCli.EXIT_OK, cli.run(new String[0]));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage: avsa"));
    }

    @Test
    void versionPrintsVersion() {
        assertEquals(AvsaCli.EXIT_OK, cli.run(new String[] {"--version"}));
        assertEquals("avsa " + AvsaCli.VERSION, out.toString(StandardCharsets.UTF_8).trim());
    }

    @Test
    void configShowsModelAndMaskedKeyOnly() {
        assertEquals(AvsaCli.EXIT_OK, cli.run(new String[] {"config"}));
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains(AppConfig.DEFAULT_MODEL));
        assertTrue(printed.contains("****4321"));
        assertTrue(!printed.contains("secret"));
    }

    @Test
    void unknownCommandFailsWithUsageError() {
        assertEquals(AvsaCli.EXIT_USAGE, cli.run(new String[] {"frobnicate"}));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unknown command: frobnicate"));
    }
}
