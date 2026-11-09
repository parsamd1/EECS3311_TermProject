package avsa.agent.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.LLMRequest;
import avsa.agent.LLMResponse;
import avsa.agent.MockLLMClient;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.market.LiveSearchSource;
import avsa.infrastructure.market.MarketDataUnavailableException;
import avsa.infrastructure.market.MarketQuery;
import avsa.infrastructure.market.MarketSearchResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LiveSearchSourceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);
    private static final MarketQuery QUERY = new MarketQuery("Mazda", "CX-5", 2020, 2024, "Toronto", "ON");
    private static final String FOUND = "https://www.kijiji.ca/v-cars-trucks/toronto/2022-mazda-cx-5-gs/111";
    private static final String MADE_UP = "https://www.kijiji.ca/v-cars-trucks/toronto/2022-mazda-cx-5-gs/999";

    private final MockLLMClient llm = new MockLLMClient();
    private final LiveSearchSource source = new LiveSearchSource(
            new AiTaskRunner(new PromptComposer(), llm, new ResponseParser(new JsonMapper()), new AgentTrace(CLOCK)),
            CLOCK);

    private static String listing(String url, int year, String model, Integer price) {
        return "{\"source\":\"Kijiji\",\"url\":\"" + url + "\",\"year\":" + year + ",\"make\":\"Mazda\",\"model\":\""
                + model + "\",\"trim\":\"GS\",\"odometerKm\":51000,\"askingPrice\":" + price
                + ",\"city\":\"Toronto\",\"province\":\"ON\",\"accidents\":null,\"description\":\"One owner\"}";
    }

    private static LLMResponse answer(String json, String... sources) {
        return new LLMResponse("I searched Kijiji. " + json, Set.of(sources), 1000, 300, 2, "end_turn");
    }

    @Test
    void keepsOnlyListingsWhosePageCameFromTheSearch() {
        llm.thenRespond(answer("{\"listings\":[" + listing(FOUND + "?ad=1", 2022, "CX-5", 28_900) + ","
                + listing(MADE_UP, 2022, "CX-5", 27_000) + "]}", FOUND));

        MarketSearchResult result = source.search(QUERY);

        assertEquals("Live search", result.sourceName());
        assertEquals(1, result.listings().size());
        assertEquals("28900", result.listings().get(0).askingPrice());
        assertEquals("51000", result.listings().get(0).odometerKm());
        assertEquals("", result.listings().get(0).accidents(), "null becomes empty, which reads as unknown");
        assertEquals(1, result.skippedRecords(), "the listing that was not in the search results");
    }

    @Test
    void dropsOtherModelsAndYearsAndKeepsMissingPricesForTheNormalizerToSkip() {
        String other = "https://www.autotrader.ca/a/mazda/cx-30/1";
        String old = "https://www.autotrader.ca/a/mazda/cx-5/2";
        llm.thenRespond(answer("{\"listings\":[" + listing(FOUND, 2022, "CX-5", null) + ","
                + listing(other, 2022, "CX-30", 25_000) + "," + listing(old, 2015, "CX-5", 12_000) + "]}", FOUND, other, old));
        MarketSearchResult result = source.search(QUERY);
        assertEquals(1, result.listings().size());
        assertEquals("", result.listings().get(0).askingPrice());
    }

    @Test
    void anAnswerWithNoVerifiableListingIsRepairedThenFallsBackToUnavailable() {
        llm.thenRespond(answer("{\"listings\":[" + listing(MADE_UP, 2022, "CX-5", 27_000) + "]}", FOUND))
                .thenRespond(answer("{\"listings\":[" + listing(MADE_UP, 2022, "CX-5", 27_000) + "]}", FOUND));
        MarketDataUnavailableException e = assertThrows(MarketDataUnavailableException.class, () -> source.search(QUERY));
        assertTrue(e.getMessage().contains("did not return usable listings"));
        assertTrue(llm.received().get(1).user().contains("None of the listing URLs appeared"));
    }

    @Test
    void anEmptyResultIsAcceptedAsNothingFound() {
        llm.thenRespond(answer("{\"listings\":[]}"));
        assertTrue(source.search(QUERY).listings().isEmpty());
    }

    @Test
    void theRequestSearchesCanadianMarketplacesNearTheSeller() {
        llm.thenRespond(answer("{\"listings\":[]}"));
        source.search(QUERY);
        LLMRequest request = llm.received().get(0);
        assertTrue(request.user().contains("Mazda CX-5"));
        assertTrue(request.user().contains("2020 to 2024"));
        assertTrue(request.user().contains("Toronto, ON"));
        assertEquals("CA", request.webSearchOptions().orElseThrow().country());
        assertTrue(request.webSearchOptions().orElseThrow().maxFetches() > 0, "may open results pages");
        assertTrue(request.webSearchOptions().orElseThrow().allowedDomains().contains("kijiji.ca"));
    }

    @Test
    void aListingWithoutACityIsPlacedInTheSearchedCityAndSaysSo() {
        String noCity = "{\"source\":\"Kijiji\",\"url\":\"" + FOUND + "\",\"year\":2022,\"make\":\"Mazda\","
                + "\"model\":\"CX-5\",\"trim\":\"GS\",\"odometerKm\":51000,\"askingPrice\":28900,\"city\":null,"
                + "\"province\":null,\"accidents\":null,\"description\":\"One owner\"}";
        llm.thenRespond(answer("{\"listings\":[" + noCity + "]}", FOUND));
        var raw = source.search(QUERY).listings().get(0);
        assertEquals("Toronto", raw.city());
        assertEquals("ON", raw.province());
        assertTrue(raw.description().contains("Location not shown"));
    }

    @Test
    void aResultsPageThatOnlyRepeatsTheSearchedCityGivesAnApproximateLocation() {
        String page = "https://www.autotrader.ca/cars/mazda/cx-5/reg_on/cit_toronto/ot_used";
        llm.thenRespond(answer("{\"listings\":[" + listing(page, 2022, "CX-5", 28_900) + ","
                + listing(FOUND, 2023, "CX-5", 31_000) + "]}", page, FOUND));
        var listings = source.search(QUERY).listings();
        assertTrue(listings.get(0).locationAssumed());
        assertTrue(listings.get(0).description().contains("location approximate"));
        assertFalse(listings.get(1).locationAssumed(), "a listing's own page states where the car is");
        assertTrue(LiveSearchSource.isResultsPage("https://www.kijiji.ca/b-cars-vehicles/city-of-toronto/mazda-cx5/k0"));
        assertFalse(LiveSearchSource.isResultsPage("https://www.autotrader.ca/offers/mazda-cx-5-gs-awd-123"));
    }

    @Test
    void severalListingsFromOneResultsPageAreKeptApart() {
        String page = "https://www.kijiji.ca/b-cars-trucks/toronto/mazda-cx-5";
        llm.thenRespond(answer("{\"listings\":[" + listing(page, 2022, "CX-5", 28_900) + ","
                + listing(page, 2023, "CX-5", 31_000) + "]}", page));
        var listings = source.search(QUERY).listings();
        var normalizer = new avsa.infrastructure.market.ListingNormalizer(new avsa.infrastructure.market.Gazetteer());
        var toronto = new avsa.domain.Location("Toronto", "ON", 43.65, -79.38);
        assertEquals(2, listings.size());
        assertFalse(normalizer.normalize(listings.get(0), toronto).orElseThrow().listingId()
                .equals(normalizer.normalize(listings.get(1), toronto).orElseThrow().listingId()));
    }

    @Test
    void withoutAnApiKeyItIsUnavailable() {
        assertFalse(new LiveSearchSource().isAvailable());
        assertThrows(MarketDataUnavailableException.class, () -> new LiveSearchSource().search(QUERY));
    }

    @Test
    void urlsMatchRegardlessOfWwwQueryAndTrailingSlash() {
        assertEquals(ListingSearchTask.canonical("https://kijiji.ca/v/1"),
                ListingSearchTask.canonical("https://www.kijiji.ca/v/1/?utm=x#top"));
        assertFalse(ListingSearchTask.canonical("https://kijiji.ca/v/1").equals(ListingSearchTask.canonical("https://kijiji.ca/v/2")));
    }

    @Test
    void searchPrefersTheSellersModelYear() {
        LLMRequest request = new PromptComposer().listingSearch(
                new MarketQuery("Mazda", "CX-5", 2016, 2024, "Toronto", "ON"));
        assertTrue(request.user().contains("from model years 2016 to 2024"));
        assertTrue(request.user().contains("The seller's vehicle is a 2020, so prefer the listings closest to that "
                + "model year"));
    }
}
