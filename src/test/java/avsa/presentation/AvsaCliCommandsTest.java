package avsa.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.MockLLMClient;
import avsa.application.AvsaApplication;
import avsa.infrastructure.AppConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvsaCliCommandsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
    private static final String[] PROFILE = {"profile", "set", "--make", "Mazda", "--model", "CX-5", "--year", "2022",
        "--trim", "GS", "--body", "suv", "--km", "49,000", "--city", "Toronto", "--province", "ON",
        "--defect", "scratch:rear door:minor"};

    @TempDir
    Path dir;

    private final MockLLMClient llm = new MockLLMClient();
    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    /** Each call is a new process, as on the command line; only the data folder carries over. */
    private int run(String key, String... args) {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        Map<String, String> env = key == null ? Map.of() : Map.of(AppConfig.API_KEY, key);
        return new AvsaCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), () -> AppConfig.load(dir.resolve("none.env"), env),
                cfg -> AvsaApplication.create(cfg, dir, CLOCK, cfg.llmApiKey().isPresent() ? llm : null), dir).run(args);
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aSellerCanSetAProfileFindComparablesAndPriceAcrossCommands() {
        assertEquals(AvsaCli.EXIT_OK, run(null, PROFILE));
        assertTrue(output().contains("Profile saved: 2022 Mazda CX-5 GS, 49,000 km, Toronto ON"));

        llm.thenRespond("{\"flags\": []}");
        assertEquals(AvsaCli.EXIT_OK, run(null, "comps", "search"));
        assertTrue(output().contains("Source: Seeded dataset"));
        assertTrue(output().contains("8 listings"));
        assertTrue(output().contains("Sources:"));

        assertEquals(AvsaCli.EXIT_OK, run(null, "price", "--strategy", "quick-sale", "--accept"));
        assertTrue(output().contains("Wholesale:"));
        assertTrue(output().contains("Explanation (template):"));
        assertTrue(output().contains("Accepted as the active price."));

        assertEquals(AvsaCli.EXIT_OK, run(null, "session", "list"));
        assertTrue(output().startsWith("* "), "the current session is marked");
        assertEquals(AvsaCli.EXIT_OK, run(null, "profile", "show"));
        assertTrue(output().contains("minor scratch on the rear door"));
    }

    @Test
    void listingAndExportCommandsWorkWithoutAKeyUsingTheTemplate() {
        run(null, PROFILE);
        run(null, "comps", "search");
        run(null, "price", "--accept");
        assertEquals(AvsaCli.EXIT_OK, run(null, "listing", "generate", "--tone", "friendly"));
        assertTrue(output().contains("Written by: template (AI unavailable)"));
        assertTrue(output().contains("Validation:  passed"));
        Path out = dir.resolve("kijiji.txt");
        assertEquals(AvsaCli.EXIT_OK, run(null, "export", "--platform", "kijiji", "--out", out.toString()));
        assertTrue(output().contains("---- Kijiji ----"));
        assertTrue(output().contains("Saved to " + out));
        assertTrue(output().contains("title: "));
        assertEquals(AvsaCli.EXIT_USAGE, run(null, "export", "--platform", "craigslist"));
    }

    @Test
    void historyForecastAndOfferCommandsWork() {
        run(null, PROFILE);
        assertEquals(AvsaCli.EXIT_OK, run(null, "history", "set", "--owners", "2", "--accidents", "0", "--lien", "none",
                "--provinces", "QC, ON"));
        assertTrue(output().contains("Provinces:   QC -> ON"));
        run(null, "comps", "search");
        run(null, "price", "--accept");
        assertEquals(AvsaCli.EXIT_OK, run(null, "forecast", "--holding", "150"));
        assertTrue(output().contains(" 3 months: worth"));
        assertEquals(AvsaCli.EXIT_OK, run(null, "offer", "add", "--amount", "18000", "--notes", "High kilometres"));
        assertTrue(output().contains("Verdict:     low ball"));
        assertTrue(output().contains("Script (template):"));
        assertEquals(AvsaCli.EXIT_OK, run(null, "offer", "list"));
        assertTrue(output().contains("$18000 from dealer: low ball"));
        assertEquals(AvsaCli.EXIT_ERROR, run(null, "history", "import", "--file", dir.resolve("none.pdf").toString()));
    }

    @Test
    void liveSearchWithoutAKeySaysItWillUseTheSeededData() {
        run(null, PROFILE);
        assertEquals(AvsaCli.EXIT_OK, run(null, "comps", "search", "--source", "live"));
        assertTrue(output().contains("Live search needs an API key"));
        assertTrue(output().contains("Note: The AI review was unavailable"));
    }

    @Test
    void doctorReportsAMissingKeyOrAWorkingConnection() {
        assertEquals(AvsaCli.EXIT_ERROR, run(null, "doctor"));
        assertTrue(output().contains("API key:    MISSING"));

        llm.thenRespond("OK");
        assertEquals(AvsaCli.EXIT_OK, run("sk-test-key-1234", "doctor"));
        assertTrue(output().contains("Connection: OK"));
        assertFalse(output().contains("sk-test-key-1234"), "the key is never printed in full");

        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE);
        assertEquals(AvsaCli.EXIT_ERROR, run("sk-test-key-1234", "doctor"));
        assertTrue(output().contains("Connection: FAILED"));
    }

    @Test
    void doctorLiveRunsARehearsalSearch() {
        run(null, PROFILE);
        llm.thenRespond("OK").thenRespond(new avsa.agent.LLMResponse("{\"listings\": [" + listing(1) + "," + listing(2)
                + "," + listing(3) + "]}", java.util.Set.of("https://www.kijiji.ca/a/1", "https://www.kijiji.ca/a/2",
                "https://www.kijiji.ca/a/3"), 100, 50, 1, "end_turn")).thenRespond("{\"flags\": []}");
        assertEquals(AvsaCli.EXIT_OK, run("sk-test-key-1234", "doctor", "--live"));
        assertTrue(output().contains("Live check: OK, 3 comparables found"), output());
    }

    private static String listing(int n) {
        return "{\"source\":\"Kijiji\",\"url\":\"https://www.kijiji.ca/a/" + n + "\",\"year\":2022,\"make\":\"Mazda\","
                + "\"model\":\"CX-5\",\"trim\":\"GS\",\"odometerKm\":5" + n + "000,\"askingPrice\":2" + (7 + n) + "000,"
                + "\"city\":\"Toronto\",\"province\":\"ON\",\"accidents\":null,\"description\":\"x\"}";
    }

    @Test
    void mistakesGiveAClearMessageAndAUsageCode() {
        assertEquals(AvsaCli.EXIT_USAGE, run(null, "profile", "set", "--make", "Mazda"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Missing option --model"));
        assertEquals(AvsaCli.EXIT_ERROR, run(null, "comps", "search"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("No vehicle profile yet"));
        assertEquals(AvsaCli.EXIT_USAGE, run(null, "comps", "search", "--source", "ebay"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("seeded, csv, live"));
    }

    @Test
    void jsonPrintsTheTraceWithoutTheKey() {
        run(null, PROFILE);
        llm.thenRespond("{\"flags\": []}");
        assertEquals(AvsaCli.EXIT_OK, run(null, "comps", "search", "--json"));
        assertTrue(output().contains("\"kind\" : \"TOOL_CALL\""));
    }
}
