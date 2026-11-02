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

    /** At least one listing must be a page the searches actually returned; otherwise the answer is not trusted. */
    @Override
    public List<String> validate(ListingSearchResult result, LLMResponse response) {
        List<String> problems = validate(result);
        if (!problems.isEmpty() || result.listings().isEmpty()) {
            return problems;
        }
        Set<String> sources = canonical(response.sourceUrls());
        boolean anyVerified = result.listings().stream().anyMatch(l -> sources.contains(canonical(l.url())));
        return anyVerified ? List.of() : List.of("None of the listing URLs appeared in your search results. Only report "
                + "listings whose page you found in the search results, using that exact URL.");
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
