package avsa.infrastructure.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.ComparableListing;
import avsa.domain.ListingAccidentStatus;
import avsa.domain.Location;
import avsa.domain.Money;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketDataSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MarketQuery QUERY = new MarketQuery("Mazda", "CX-5", 2020, 2024);
    private static final Location TORONTO = new Location("Toronto", "ON", 43.6532, -79.3832);

    private static RawListing raw(String price) {
        return new RawListing("Autotrader", "https://example.test/ad?position=2", "", "Mazda", "CX-5", "2022", "GX AWD",
                "51,000", price, "Hamilton", "ON", "clean", "1 accident and 1 glass record found", "text");
    }

    /** A source whose availability and failure can be controlled. */
    private static final class FakeSource implements MarketDataSource {
        boolean available = true;
        boolean failing;
        int searches;

        @Override
        public String name() {
            return "Fake";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public MarketSearchResult search(MarketQuery query) {
            searches++;
            if (failing) {
                throw new MarketDataUnavailableException("down", null);
            }
            return new MarketSearchResult(name(), List.of(raw("28000")), 0, false, NOW);
        }
    }

    private static MarketDataSource seededWith(String sourceName) {
        return new MarketDataSource() {
            @Override
            public String name() {
                return sourceName;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public MarketSearchResult search(MarketQuery query) {
                return new MarketSearchResult(sourceName, List.of(), 0, false, NOW);
            }
        };
    }

    @Test
    void proxyForwardsToAnAvailableSource() {
        FakeSource real = new FakeSource();
        MarketSearchResult r = new CachingMarketDataSource(real, seededWith("Seed"), new ListingCache()).search(QUERY);
        assertEquals("Fake", r.sourceName());
        assertFalse(r.fromCache());
        assertEquals(1, real.searches);
    }

    @Test
    void proxyUsesTheCacheWhenTheSourceBecomesUnavailableOrFails() {
        FakeSource real = new FakeSource();
        CachingMarketDataSource proxy = new CachingMarketDataSource(real, seededWith("Seed"), new ListingCache());
        proxy.search(QUERY);

        real.available = false;
        MarketSearchResult cached = proxy.search(QUERY);
        assertTrue(cached.fromCache());
        assertEquals(NOW, cached.retrievedAt());
        assertEquals(1, cached.listings().size());
        assertEquals(1, real.searches, "an unavailable source is not asked");

        real.available = true;
        real.failing = true;
        assertTrue(proxy.search(QUERY).fromCache());
    }

    @Test
    void proxyFallsBackToTheSeededDatasetWithoutACachedResult() {
        FakeSource real = new FakeSource();
        real.available = false;
        MarketSearchResult r = new CachingMarketDataSource(real, seededWith("Seed"), new ListingCache()).search(QUERY);
        assertEquals("Seed", r.sourceName());
        assertFalse(r.fromCache());
    }

    @Test
    void liveSearchReportsItselfUnavailable() {
        assertFalse(new LiveSearchSource().isAvailable());
        assertThrows(MarketDataUnavailableException.class, () -> new LiveSearchSource().search(QUERY));
    }

    @Test
    void csvReaderHandlesQuotesMakesAndMissingFiles(@TempDir Path dir) throws IOException {
        Path csv = Files.writeString(dir.resolve("a.csv"), "﻿source,make,model,year,description\n"
                + "Kijiji,Mazda,CX-5,2022,\"Clean, \"\"one owner\"\"\"\r\n"
                + "Kijiji,Honda,Civic,2022,other make\n"
                + "Kijiji,mazda,cx-5,2015,too old\n\n");
        CsvImportSource source = new CsvImportSource(csv, CLOCK);
        assertTrue(source.isAvailable());
        List<RawListing> listings = source.search(QUERY).listings();
        assertEquals(1, listings.size());
        assertEquals("Clean, \"one owner\"", listings.get(0).description());

        CsvImportSource missing = new CsvImportSource(dir.resolve("none.csv"), CLOCK);
        assertFalse(missing.isAvailable());
        assertThrows(MarketDataUnavailableException.class, () -> missing.search(QUERY));
    }

    @Test
    void normalizerCleansARecord() {
        ComparableListing l = new ListingNormalizer(new Gazetteer()).normalize(raw("$28,900"), TORONTO).orElseThrow();
        assertEquals("AutoTrader", l.source());
        assertEquals("GX", l.trim());
        assertEquals(51_000, l.odometerKm());
        assertEquals(Money.cad(28_900), l.askingPrice());
        assertEquals("https://example.test/ad", l.url());
        assertEquals(ListingAccidentStatus.REPORTED, l.accidentStatus());
        assertEquals(58, l.distanceKm(), 3, "Toronto to Hamilton");
    }

    @Test
    void normalizerRejectsUnusableRecords() {
        ListingNormalizer normalizer = new ListingNormalizer(new Gazetteer());
        assertTrue(normalizer.normalize(raw(""), TORONTO).isEmpty());
        assertTrue(normalizer.normalize(raw("call us"), TORONTO).isEmpty());
        assertTrue(normalizer.normalize(raw("0"), TORONTO).isEmpty());
    }

    @Test
    void accidentTextIsClassified() {
        assertEquals(ListingAccidentStatus.NONE, ListingNormalizer.accidentStatus("No accidents reported"));
        assertEquals(ListingAccidentStatus.NONE, ListingNormalizer.accidentStatus("1 glass record found"));
        assertEquals(ListingAccidentStatus.REPORTED, ListingNormalizer.accidentStatus("Accident reported: minor damage"));
        assertEquals(ListingAccidentStatus.UNKNOWN, ListingNormalizer.accidentStatus("report unavailable"));
        assertEquals(ListingAccidentStatus.UNKNOWN, ListingNormalizer.accidentStatus(""));
    }

    @Test
    void gazetteerResolvesCitiesIgnoringCase() {
        Gazetteer gazetteer = new Gazetteer();
        assertEquals("Toronto", gazetteer.resolve(" toronto ", "on").orElseThrow().city());
        assertTrue(gazetteer.resolve("Atlantis", "ON").isEmpty());
    }
}
