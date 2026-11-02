package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.BodyStyle;
import avsa.domain.ComparableListing;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FlagCode;
import avsa.domain.FuelType;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.ListingFlag;
import avsa.domain.Location;
import avsa.domain.MarketSnapshot;
import avsa.domain.Money;
import avsa.domain.Severity;
import avsa.domain.Transmission;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.market.CachingMarketDataSource;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.ListingCache;
import avsa.infrastructure.market.ListingNormalizer;
import avsa.infrastructure.market.LiveSearchSource;
import avsa.infrastructure.market.MarketDataSource;
import avsa.infrastructure.market.SeededDatasetSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparableFinderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private static final String HEADER = "source,url,listing_date,make,model,year,trim,body_style,transmission,"
            + "fuel_type,odometer_km,asking_price,city,province,seller_type,title_status,accidents,description\n";

    @TempDir
    Path dir;

    private static VehicleProfile cx5() {
        return VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.6532, -79.3832))
                .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door",
                        Severity.MINOR, "About 25 cm hairline", null))))
                .build();
    }

    private static ComparableFinder finder(MarketDataSource source) {
        return new ComparableFinder(source, new ListingNormalizer(new Gazetteer()), new Gazetteer(),
                new OutlierFilter(), new SimilarityScorer(), CLOCK);
    }

    private static String row(String id, int year, String trim, int km, int price, String city) {
        return "Kijiji,https://example.test/" + id + ",,Mazda,CX-5," + year + "," + trim + ",SUV,Automatic,Gasoline," + km
                + "," + price + "," + city + ",ON,Dealer,clean,No accidents reported,Listing " + id + "\n";
    }

    private MarketSnapshot search(SearchRequest request, String... rows) throws IOException {
        Path csv = Files.writeString(dir.resolve("listings.csv"), HEADER + String.join("", rows));
        return finder(new SeededDatasetSource(csv, CLOCK)).find(cx5(), request);
    }

    private static List<Integer> prices(MarketSnapshot s) {
        return s.rankedListings().stream().map(l -> (int) l.askingPrice().toDouble()).toList();
    }

    @Test
    void ranksTheClosestMatchFirstAndExplainsTheScore() throws IOException {
        MarketSnapshot s = search(SearchRequest.defaults(),
                row("far-km", 2022, "GS", 149_000, 20_000, "Toronto"),
                row("close", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("older", 2020, "GS", 50_000, 25_000, "Hamilton"));
        ComparableListing best = s.rankedListings().get(0);
        assertEquals(28_000, (int) best.askingPrice().toDouble());
        assertEquals(best.score(), best.breakdown().orElseThrow().total(), 1e-9);
        assertTrue(best.score() > s.rankedListings().get(1).score());
        assertFalse(best.reviewed(), "unreviewed until the AI review runs");
        assertEquals(3, s.stats().count());
        assertEquals(Money.cad(20_000), s.stats().min());
        assertEquals(Money.cad(25_000), s.stats().median());
        assertEquals(Money.cad(28_000), s.stats().max());
    }

    @Test
    void limitsResultsAndDistanceAndYearsAndTrim() throws IOException {
        MarketSnapshot s = search(SearchRequest.defaults(),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("b", 2023, "GS AWD", 50_000, 28_500, "Mississauga"),
                row("c", 2021, "GS Comfort", 50_000, 27_500, "Hamilton"),
                row("too-far", 2022, "GS", 50_000, 28_000, "Ottawa"),
                row("too-old", 2019, "GS", 50_000, 28_000, "Toronto"),
                row("other-trim", 2022, "Signature", 50_000, 28_000, "Toronto"));
        assertEquals(3, s.rankedListings().size());
        assertTrue(s.warnings().isEmpty(), () -> s.warnings().toString());
        assertTrue(s.rankedListings().stream().allMatch(l -> l.distanceKm() <= 100));

        MarketSnapshot limited = search(new SearchRequest(100, 2, 2, true, null),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("b", 2023, "GS", 50_000, 28_500, "Toronto"),
                row("c", 2021, "GS", 50_000, 27_500, "Toronto"));
        assertEquals(2, limited.rankedListings().size());
    }

    @Test
    void widensRadiusThenYearsThenTrim() throws IOException {
        MarketSnapshot radius = search(SearchRequest.defaults(),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("b", 2022, "GS", 50_000, 28_100, "Kingston"),
                row("c", 2022, "GS", 50_000, 28_200, "Kingston"));
        assertEquals(400, radius.radiusKm(), "doubled until Kingston is covered");
        assertEquals(3, radius.rankedListings().size());
        assertFalse(radius.lowConfidence());
        assertTrue(radius.warnings().get(0).contains("widened"));

        MarketSnapshot trim = search(SearchRequest.defaults(),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("b", 2022, "Signature", 50_000, 31_000, "Toronto"),
                row("c", 2022, "GT", 50_000, 30_000, "Toronto"));
        assertEquals(3, trim.rankedListings().size());
        assertTrue(trim.warnings().get(0).contains("any trim"));
    }

    @Test
    void tooFewAfterWideningIsLowConfidenceAndSuggestsImport() throws IOException {
        MarketSnapshot s = search(SearchRequest.defaults(), row("a", 2022, "GS", 50_000, 28_000, "Toronto"));
        assertTrue(s.lowConfidence());
        assertEquals(1, s.rankedListings().size());
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("CSV")));
    }

    @Test
    void malformedRecordsAreSkippedAndCounted() throws IOException {
        MarketSnapshot s = search(SearchRequest.defaults(),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("b", 2022, "GS", 50_000, 28_100, "Toronto"),
                row("c", 2022, "GS", 50_000, 28_200, "Toronto"),
                row("no-price", 2022, "GS", 50_000, 0, "Toronto"),
                row("unknown-city", 2022, "GS", 50_000, 28_000, "Atlantis"),
                "Kijiji,https://example.test/bad-year,,Mazda,CX-5,soon,GS,SUV,Automatic,Gasoline,1,1,Toronto,ON,D,c,n,d\n",
                "too,few,columns\n");
        assertEquals(3, s.rankedListings().size());
        assertEquals(4, s.skippedRecords());
        assertTrue(s.warnings().get(0).startsWith("4 listing records were skipped"));
    }

    @Test
    void duplicatesAndPriceOutliersAreRemoved() throws IOException {
        MarketSnapshot s = search(SearchRequest.defaults(),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("a", 2022, "GS", 50_000, 28_000, "Toronto"),
                row("b", 2022, "GS", 50_000, 28_500, "Toronto"),
                row("c", 2022, "GS", 50_000, 27_500, "Toronto"),
                row("d", 2022, "GS", 50_000, 28_200, "Toronto"),
                row("too-cheap", 2022, "GS", 50_000, 9_000, "Toronto"));
        assertEquals(List.of(27_500, 28_000, 28_200, 28_500), prices(s).stream().sorted().toList());
    }

    @Test
    void listingsMatchingTheSellersAccidentHistoryScoreHigher() throws IOException {
        VehicleHistory clean = new VehicleHistory(null, 2, 1, LienStatus.NONE, List.of(), HistorySourceType.MANUAL, true,
                List.of(), List.of());
        Path csv = Files.writeString(dir.resolve("h.csv"), HEADER
                + "Kijiji,https://example.test/x,,Mazda,CX-5,2022,GS,SUV,Automatic,Gasoline,50000,28000,Toronto,ON,D,clean,"
                + "1 accident reported,x\n"
                + "Kijiji,https://example.test/y,,Mazda,CX-5,2022,GS,SUV,Automatic,Gasoline,50000,28100,Toronto,ON,D,clean,"
                + "No accidents reported,y\n"
                + "Kijiji,https://example.test/z,,Mazda,CX-5,2022,GS,SUV,Automatic,Gasoline,50000,28200,Toronto,ON,D,clean,"
                + ",z\n");
        MarketSnapshot s = finder(new SeededDatasetSource(csv, CLOCK)).find(cx5(),
                SearchRequest.defaults().withHistory(clean));
        assertEquals(List.of(28_100, 28_200, 28_000), prices(s), "clean, then unknown, then accident");
    }

    @Test
    void poorConditionOrBrandedTitleLowersTheScore() throws IOException {
        Path csv = Files.writeString(dir.resolve("c.csv"), HEADER
                + "Kijiji,https://example.test/x,,Mazda,CX-5,2022,GS,SUV,Automatic,Gasoline,50000,28000,Toronto,ON,D,clean,,"
                + "Vehicle is not drivable\n"
                + "Kijiji,https://example.test/y,,Mazda,CX-5,2022,GS,SUV,Automatic,Gasoline,50000,28100,Toronto,ON,D,clean,,y\n"
                + "Kijiji,https://example.test/z,,Mazda,CX-5,2022,GS,SUV,Automatic,Gasoline,50000,28200,Toronto,ON,D,"
                + "rebuilt,,z\n");
        MarketSnapshot s = finder(new SeededDatasetSource(csv, CLOCK)).find(cx5(), SearchRequest.defaults());
        assertEquals(28_100, (int) s.rankedListings().get(0).askingPrice().toDouble());
    }

    @Test
    void reviewFlagsOnlyApplyFixedPenaltiesAndReRank() throws IOException {
        ComparableFinder finder;
        Path csv = Files.writeString(dir.resolve("r.csv"), HEADER
                + row("a", 2022, "GS", 50_000, 28_000, "Toronto") + row("b", 2022, "GS", 60_000, 27_000, "Toronto"));
        finder = finder(new SeededDatasetSource(csv, CLOCK));
        MarketSnapshot s = finder.find(cx5(), new SearchRequest(100, 10, 2, false, null));
        ComparableListing top = s.rankedListings().get(0);
        double before = top.score();

        MarketSnapshot reviewed = finder.applyReview(s, Map.of(top.listingId(),
                List.of(new ListingFlag(FlagCode.SALVAGE_TITLE, "Description mentions salvage"))));

        assertEquals(before - 0.50, top.score(), 1e-9);
        assertEquals(1, top.flags().size());
        assertEquals(s.id(), reviewed.id());
        assertEquals(top.listingId(), reviewed.rankedListings().get(1).listingId(), "dropped below the other listing");
        assertTrue(reviewed.rankedListings().stream().allMatch(ComparableListing::reviewed));
    }

    @Test
    void aProfileWithoutATrimDoesNotForceAWiderSearch() throws IOException {
        Path csv = Files.writeString(dir.resolve("t.csv"), HEADER + row("a", 2022, "GS", 50_000, 28_000, "Toronto")
                + row("b", 2022, "GT", 50_000, 28_100, "Toronto") + row("c", 2022, "GX", 50_000, 28_200, "Toronto"));
        VehicleProfile noTrim = VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.6532, -79.3832)).build();
        MarketSnapshot s = finder(new SeededDatasetSource(csv, CLOCK)).find(noTrim, SearchRequest.defaults());
        assertEquals(3, s.rankedListings().size());
        assertEquals(100, s.radiusKm());
        assertTrue(s.warnings().isEmpty(), () -> s.warnings().toString());
    }

    @Test
    void theSourceIsSearchedOnceEvenWhenTheSearchWidens() throws IOException {
        Path csv = Files.writeString(dir.resolve("w.csv"), HEADER + row("a", 2022, "GS", 50_000, 28_000, "Toronto")
                + row("b", 2019, "GS", 50_000, 24_000, "Toronto") + row("c", 2025, "GS", 50_000, 33_000, "Toronto"));
        int[] searches = {0};
        SeededDatasetSource seeded = new SeededDatasetSource(csv, CLOCK);
        MarketDataSource counting = new MarketDataSource() {
            public String name() { return "Counting"; }
            public boolean isAvailable() { return true; }
            public avsa.infrastructure.market.MarketSearchResult search(avsa.infrastructure.market.MarketQuery q) {
                searches[0]++;
                return seeded.search(q);
            }
        };
        MarketSnapshot s = finder(counting).find(cx5(), SearchRequest.defaults());
        assertEquals(1, searches[0]);
        assertEquals(3, s.rankedListings().size(), "found after widening the years to +/-3");
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("model years +/-3")), () -> s.warnings().toString());
    }

    @Test
    void aListingWithAnAssumedLocationGetsANeutralDistanceScore() {
        ListingNormalizer normalizer = new ListingNormalizer(new Gazetteer());
        Location toronto = new Location("Toronto", "ON", 43.6532, -79.3832);
        avsa.infrastructure.market.RawListing assumed = new avsa.infrastructure.market.RawListing("Kijiji",
                "https://example.test/x", "", "Mazda", "CX-5", "2022", "GS", "50000", "28000", "Toronto", "ON", "", "",
                "", true);
        ComparableListing listing = normalizer.normalize(assumed, toronto).orElseThrow();
        assertTrue(listing.locationApproximate());
        assertEquals(0.10 * SimilarityScorer.NEUTRAL, new SimilarityScorer().score(cx5(), null, listing).distance(), 1e-9);
    }

    @Test
    void invalidProfileIsBlocked() {
        VehicleProfile noLocation = VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).build();
        assertThrows(IllegalArgumentException.class,
                () -> finder(new LiveSearchSource()).find(noLocation, SearchRequest.defaults()));
    }

    @Test
    void theShippedDatasetGivesEnoughComparablesForTheAuthorsCar() {
        Path shipped = Path.of("data", "listings.csv");
        MarketDataSource proxy = new CachingMarketDataSource(new LiveSearchSource(),
                new SeededDatasetSource(shipped, CLOCK), new ListingCache());

        MarketSnapshot s = finder(proxy).find(cx5(), SearchRequest.defaults());

        assertEquals("Seeded dataset", s.sourceName());
        assertEquals(0, s.skippedRecords());
        assertEquals(8, s.rankedListings().size());
        assertFalse(s.lowConfidence());
        assertEquals(100, s.radiusKm());
        assertTrue(s.rankedListings().stream().allMatch(l -> l.trim().startsWith("GS")));
        assertTrue(s.rankedListings().stream().allMatch(l -> l.url().indexOf('?') < 0), "tracking parameters removed");
        assertTrue(s.warnings().get(0).startsWith("Live search was unavailable"));
    }
}
