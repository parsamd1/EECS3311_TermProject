package avsa.agent;

import java.util.List;

/**
 * Lets the model search the web during a request, limited to the given sites and localized to
 * the seller's area. With {@code maxFetches} above zero it may also open pages the searches found.
 *
 * @param maxFetchTokens the most text taken from one fetched page
 */
public record WebSearchOptions(int maxUses, List<String> allowedDomains, String city, String region, String country,
                               int maxFetches, int maxFetchTokens) {

    public WebSearchOptions {
        if (maxUses <= 0) {
            throw new IllegalArgumentException("maxUses must be positive");
        }
        allowedDomains = List.copyOf(allowedDomains);
    }
}
