package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.ExportedListing;
import avsa.domain.GeneratedListing;
import avsa.domain.Money;
import avsa.domain.Platform;
import avsa.domain.PlatformProfile;
import avsa.domain.Tone;
import avsa.domain.ValidationReport;
import avsa.domain.ValidationRule;
import avsa.domain.Violation;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.PlatformProfileLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListingExporterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final String DISCLOSURE = "Minor scratch on the rear door.";
    private static final ValidationReport VALID = new ValidationReport(CLOCK.instant(), List.of());

    private final ExporterFactory factory = new ExporterFactory(new PlatformProfileLoader(), CLOCK);

    private static GeneratedListing listing(String description, ValidationReport report) {
        return new GeneratedListing("2022 Mazda CX-5 GS - 49,000 km", description, List.of("Winter tires", "Heated seats"),
                List.of("2024-12-13: Oil change"), DISCLOSURE, Money.cad(29_500), List.of("Mazda CX-5", "used suv"),
                Tone.PROFESSIONAL, false, false, false, report);
    }

    private static GeneratedListing valid() {
        return listing("A well kept vehicle.", VALID);
    }

    private static PlatformProfile profile(int titleLimit, int bodyLimit, LocalDate verified) {
        return new PlatformProfile(Platform.KIJIJI, Map.of("title", titleLimit, "body", bodyLimit),
                List.of("title", "price", "description"), "$#,##0", verified);
    }

    private static final LocalDate RECENT = LocalDate.of(2026, 10, 1);

    /** The length of everything but the trimmable section being tested, for setting up exact limits. */
    private static int bodyLengthWithout(ExportedListing exported, String... sections) {
        Map<String, String> fields = new LinkedHashMap<>(exported.fields());
        fields.remove(ListingExporter.TITLE);
        fields.remove(ListingExporter.PRICE);
        for (String section : sections) {
            fields.remove(section);
        }
        return String.join("\n\n", fields.values()).length();
    }

    @Test
    void factoryCreatesTheExporterForEachPlatform() {
        assertInstanceOf(AutoTraderExporter.class, factory.createExporter(Platform.AUTOTRADER));
        assertInstanceOf(KijijiExporter.class, factory.createExporter(Platform.KIJIJI));
        assertInstanceOf(FacebookMarketplaceExporter.class, factory.createExporter(Platform.FACEBOOK_MARKETPLACE));
    }

    @Test
    void everyPlatformKeepsThePriceDisclosureAndAllSections() {
        for (Platform platform : Platform.values()) {
            ExportedListing e = factory.createExporter(platform).export(valid());
            assertTrue(e.text().contains("$29,500"), platform.name());
            assertTrue(e.text().contains(DISCLOSURE), platform.name());
            assertTrue(e.text().contains("2022 Mazda CX-5 GS - 49,000 km"), platform.name());
            assertTrue(e.text().contains("Winter tires"), platform.name());
            assertTrue(e.text().contains("Oil change"), platform.name());
            assertTrue(e.withinLimits(), platform.name());
            assertTrue(e.trimmedSections().isEmpty(), platform.name());
            assertEquals(platform, e.profile().platform());
        }
    }

    @Test
    void platformsFormatDifferently() {
        String kijiji = factory.createExporter(Platform.KIJIJI).export(valid()).text();
        String autotrader = factory.createExporter(Platform.AUTOTRADER).export(valid()).text();
        String facebook = factory.createExporter(Platform.FACEBOOK_MARKETPLACE).export(valid()).text();
        assertTrue(kijiji.contains("- Winter tires"));
        assertTrue(autotrader.startsWith("Title: "));
        assertTrue(autotrader.contains("Features: Winter tires, Heated seats"));
        assertTrue(facebook.contains("| $29,500"));
        assertTrue(facebook.contains("Please note: " + DISCLOSURE));
    }

    @Test
    void aListingThatFailedValidationIsBlockedUnlessOverridden() {
        ValidationReport failed = new ValidationReport(CLOCK.instant(),
                List.of(new Violation(ValidationRule.BANNED_CLAIM, "Remove it.")));
        ListingExporter exporter = factory.createExporter(Platform.KIJIJI);
        assertThrows(ExportBlockedException.class, () -> exporter.export(listing("Text.", failed)));
        assertThrows(ExportBlockedException.class, () -> exporter.export(listing("Text.", null)), "never validated");
        assertTrue(exporter.export(listing("Text.", failed).withOverride()).text().contains(DISCLOSURE));
    }

    @Test
    void trimsOptionalSectionsInTheFixedOrder() {
        ExportedListing full = new KijijiExporter(profile(100, 5000, RECENT), CLOCK).export(valid());

        int withoutKeywords = bodyLengthWithout(full, ListingExporter.KEYWORDS);
        ExportedListing one = new KijijiExporter(profile(100, withoutKeywords, RECENT), CLOCK).export(valid());
        assertEquals(List.of(ListingExporter.KEYWORDS), one.trimmedSections());
        assertTrue(one.withinLimits());
        assertTrue(one.text().contains("Winter tires"));
        assertFalse(one.text().contains("used suv"));

        int noFeatures = bodyLengthWithout(full, ListingExporter.KEYWORDS, ListingExporter.FEATURES);
        ExportedListing two = new KijijiExporter(profile(100, noFeatures, RECENT), CLOCK).export(valid());
        assertEquals(List.of(ListingExporter.KEYWORDS, ListingExporter.FEATURES), two.trimmedSections());
        assertTrue(two.text().contains("Oil change"));

        int noMaintenance = bodyLengthWithout(full, ListingExporter.KEYWORDS, ListingExporter.FEATURES,
                ListingExporter.MAINTENANCE);
        ExportedListing three = new KijijiExporter(profile(100, noMaintenance, RECENT), CLOCK).export(valid());
        assertEquals(List.of(ListingExporter.KEYWORDS, ListingExporter.FEATURES, ListingExporter.MAINTENANCE),
                three.trimmedSections());
        assertTrue(three.withinLimits());
    }

    @Test
    void neverRemovesTheDisclosureOrPriceEvenWhenStillTooLong() {
        ExportedListing e = new KijijiExporter(profile(100, 10, RECENT), CLOCK).export(valid());
        assertFalse(e.withinLimits());
        assertTrue(e.text().contains(DISCLOSURE));
        assertTrue(e.text().contains("$29,500"));
        assertTrue(e.warnings().get(0).contains("Make shorter"));
    }

    @Test
    void aTitleOverTheLimitIsReportedNotCut() {
        ExportedListing e = new KijijiExporter(profile(10, 5000, RECENT), CLOCK).export(valid());
        assertFalse(e.withinLimits());
        assertTrue(e.warnings().get(0).contains("title"));
        assertTrue(e.text().contains("2022 Mazda CX-5 GS - 49,000 km"));
    }

    @Test
    void aSubclassThatDropsTheDisclosureOrPriceCannotExport() {
        ListingExporter dropsDisclosure = new ListingExporter(profile(100, 5000, RECENT), CLOCK) {
            @Override
            protected Map<String, String> mapFields(GeneratedListing listing) {
                return Map.of(TITLE, listing.title(), PRICE, formatPrice(listing.price()));
            }

            @Override
            protected String render(Map<String, String> fields) {
                return fields.get(TITLE) + " " + fields.get(PRICE);
            }
        };
        assertTrue(assertThrows(ExportBlockedException.class, () -> dropsDisclosure.export(valid())).getMessage()
                .contains("condition disclosure"));

        ListingExporter wrongPrice = new ListingExporter(profile(100, 5000, RECENT), CLOCK) {
            @Override
            protected Map<String, String> mapFields(GeneratedListing listing) {
                return Map.of(PRICE, "$1", DISCLOSURE, listing.conditionDisclosure());
            }

            @Override
            protected String render(Map<String, String> fields) {
                return fields.get(DISCLOSURE) + " " + fields.get(PRICE);
            }
        };
        assertTrue(assertThrows(ExportBlockedException.class, () -> wrongPrice.export(valid())).getMessage()
                .contains("price"));
    }

    @Test
    void aMissingRequiredFieldIsBlocked() {
        GeneratedListing noTitle = new GeneratedListing(" ", "Text.", List.of(), List.of(), DISCLOSURE, Money.cad(1),
                List.of(), Tone.CONCISE, false, false, false, VALID);
        assertTrue(assertThrows(ExportBlockedException.class, () -> factory.createExporter(Platform.KIJIJI).export(noTitle))
                .getMessage().contains("title"));
    }

    @Test
    void emptySectionsAreLeftOut() {
        GeneratedListing bare = new GeneratedListing("2022 Mazda CX-5", "Text.", List.of(), List.of(), DISCLOSURE,
                Money.cad(29_500), List.of(), Tone.CONCISE, false, false, false, VALID);
        ExportedListing e = factory.createExporter(Platform.AUTOTRADER).export(bare);
        assertFalse(e.text().contains("Features"));
        assertFalse(e.text().contains("Maintenance"));
        assertFalse(e.fields().containsKey(ListingExporter.KEYWORDS));
    }

    @Test
    void unverifiedOrOldLimitsAreFlagged() {
        assertTrue(factory.createExporter(Platform.KIJIJI).export(valid()).warnings().stream()
                .anyMatch(w -> w.contains("have not been verified")), "shipped limits are unverified");
        assertTrue(new KijijiExporter(profile(100, 5000, LocalDate.of(2025, 1, 1)), CLOCK).export(valid()).warnings()
                .get(0).contains("last verified on 2025-01-01"));
        assertTrue(new KijijiExporter(profile(100, 5000, RECENT), CLOCK).export(valid()).warnings().isEmpty());
    }

    @Test
    void shippedConfigMatchesBuiltInAndABadFileFallsBack(@TempDir Path dir) throws IOException {
        PlatformProfileLoader shipped = new PlatformProfileLoader();
        Path corrupt = Files.writeString(dir.resolve("bad.json"), "{ not json");
        PlatformProfileLoader fallback = new PlatformProfileLoader(corrupt, new JsonMapper());
        PlatformProfileLoader missing = new PlatformProfileLoader(dir.resolve("none.json"), new JsonMapper());
        for (Platform platform : Platform.values()) {
            assertEquals(fallback.load(platform), shipped.load(platform));
            assertEquals(fallback.load(platform), missing.load(platform));
        }
        Path custom = Files.writeString(dir.resolve("c.json"), "[{\"platform\":\"KIJIJI\",\"fieldLimits\":{\"title\":50},"
                + "\"requiredFields\":[\"title\"],\"priceFormat\":\"#,##0\",\"lastVerified\":\"2026-10-01\"}]");
        PlatformProfile kijiji = new PlatformProfileLoader(custom, new JsonMapper()).load(Platform.KIJIJI);
        assertEquals(50, kijiji.limitOf("title").orElseThrow());
        assertEquals(RECENT, kijiji.lastVerified());
    }
}
