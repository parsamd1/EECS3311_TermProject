package avsa.infrastructure.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.infrastructure.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListingCacheTest {

    private static final MarketQuery QUERY = new MarketQuery("Mazda", "CX-5", 2018, 2026, "Toronto", "ON");

    @Test
    void aSavedLiveResultIsStillThereAfterARestart(@TempDir Path dir) {
        Path file = dir.resolve("cache").resolve("listings.json");
        RawListing raw = new RawListing("Kijiji", "https://www.kijiji.ca/a/1", "", "Mazda", "CX-5", "2022", "GS", "51000",
                "28900", "Toronto", "ON", "", "", "One owner", true);
        Instant when = Instant.parse("2026-11-03T15:00:00Z");
        new ListingCache(file, new JsonMapper()).put(QUERY, new MarketSearchResult("Live search", List.of(raw), 2, false,
                when));

        MarketSearchResult loaded = new ListingCache(file, new JsonMapper()).latest("Live search", QUERY).orElseThrow();
        assertEquals("Live search", loaded.sourceName());
        assertEquals(when, loaded.retrievedAt());
        assertEquals(raw, loaded.listings().get(0));
        assertEquals(2, loaded.skippedRecords());
    }

    @Test
    void aCorruptFileStartsEmpty(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("listings.json"), "{ not json");
        assertTrue(new ListingCache(file, new JsonMapper()).latest("Live search", QUERY).isEmpty());
    }

    @Test
    void theProxyFallsBackToYesterdaysLiveResultWhenLiveSearchFails(@TempDir Path dir) {
        Path file = dir.resolve("listings.json");
        new ListingCache(file, new JsonMapper()).put(QUERY, new MarketSearchResult("Live search", List.of(), 0, false,
                Instant.parse("2026-11-02T15:00:00Z")));
        MarketSearchResult result = new CachingMarketDataSource(new LiveSearchSource(),
                new SeededDatasetSource(Path.of("data", "listings.csv"), java.time.Clock.systemUTC()),
                new ListingCache(file, new JsonMapper())).search(QUERY);
        assertEquals("Live search", result.sourceName());
        assertTrue(result.fromCache());
    }

    @Test
    void aCsvImportDoesNotReplaceTheSavedLiveResult(@TempDir Path dir) {
        Path file = dir.resolve("listings.json");
        ListingCache cache = new ListingCache(file, new JsonMapper());
        Instant live = Instant.parse("2026-11-08T20:00:00Z");
        cache.put(QUERY, new MarketSearchResult("Live search", List.of(), 0, false, live));
        cache.put(QUERY, new MarketSearchResult("Imported CSV", List.of(), 2, false, live.plusSeconds(600)));

        ListingCache reloaded = new ListingCache(file, new JsonMapper());
        assertEquals(live, reloaded.latest("Live search", QUERY).orElseThrow().retrievedAt());
        assertEquals(2, reloaded.latest("Imported CSV", QUERY).orElseThrow().skippedRecords());
        MarketSearchResult fallback = new CachingMarketDataSource(new LiveSearchSource(),
                new SeededDatasetSource(Path.of("data", "listings.csv"), java.time.Clock.systemUTC()), reloaded)
                .search(QUERY);
        assertEquals("Live search", fallback.sourceName(), "a failed live search falls back to live, not to the CSV");
    }
}
