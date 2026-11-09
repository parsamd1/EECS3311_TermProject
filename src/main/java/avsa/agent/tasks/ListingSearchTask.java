package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.LLMResponse;
import avsa.agent.PromptComposer;
import avsa.infrastructure.market.MarketQuery;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asks the model to search online marketplaces for comparable listings (F02 live search).
 * The model only reports what it found; code checks that each listing's page really came
 * from the search results, and the usual normalizer and scorer handle the rest.
 */
public final class ListingSearchTask implements AiTask<ListingSearchResult> {

    /** Fewer usable listings than this is not a useful live result (the pricing engine needs three). */
    static final int MIN_USABLE = 3;

    private final MarketQuery query;

    public ListingSearchTask(MarketQuery query) {
        this.query = query;
    }

    @Override
    public String name() {
        return "listing-search";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.listingSearch(query);
    }

    @Override
    public Class<ListingSearchResult> resultType() {
        return ListingSearchResult.class;
    }

    @Override
    public List<String> validate(ListingSearchResult result) {
        return result.listings() == null ? List.of("The JSON must have a \"listings\" array.") : List.of();
    }

    /**
     * The answer is used only when at least {@link #MIN_USABLE} listings come from a page the searches really
     * returned and give the year, kilometres and price; otherwise the model is asked again, and after that the
     * caching proxy falls back to the last saved result or the seeded data.
     */
    @Override
    public List<String> validate(ListingSearchResult result, LLMResponse response) {
        List<String> problems = validate(result);
        if (!problems.isEmpty()) {
            return problems;
        }
        long usable = usable(result, response.sourceUrls(), query).size();
        return usable >= MIN_USABLE ? List.of() : List.of("Only " + usable + " of your listings came from a page that "
                + "appeared in your search or fetch results and gave the year, kilometres and price. Report at least "
                + MIN_USABLE + " such listings: open the results or listing pages, copy each listing's URL exactly as "
                + "it appeared in your search or fetch results, and copy its year, kilometres and price from the page.");
    }

    /** Listings of the searched model and years, from a page the searches returned, with year, km and price. */
    static List<FoundListing> usable(ListingSearchResult result, Set<String> sourceUrls, MarketQuery query) {
        return verified(result, sourceUrls).stream()
                .filter(l -> l.year() != null && l.odometerKm() != null && l.askingPrice() != null && l.askingPrice() > 0)
                .filter(l -> l.year() >= query.minYear() && l.year() <= query.maxYear())
                .filter(l -> query.model().equalsIgnoreCase(l.model() == null ? "" : l.model().strip()))
                .toList();
    }

    @Override
    public ListingSearchResult fallback() {
        return new ListingSearchResult(List.of());
    }

    /** The listings whose page came from the search results. */
    public static List<FoundListing> verified(ListingSearchResult result, Set<String> sourceUrls) {
        Set<String> sources = canonical(sourceUrls);
        return result.listings().stream().filter(l -> sources.contains(canonical(l.url()))).toList();
    }

    private static Set<String> canonical(Set<String> urls) {
        return urls.stream().map(ListingSearchTask::canonical).collect(Collectors.toSet());
    }

    /** Scheme, "www.", query, fragment and a trailing slash do not make a different page. */
    static String canonical(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.strip());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return host + path;
        } catch (IllegalArgumentException e) {
            return url.strip();
        }
    }
}
