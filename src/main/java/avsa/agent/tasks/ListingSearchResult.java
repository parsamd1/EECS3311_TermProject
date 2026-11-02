package avsa.agent.tasks;

import java.util.List;

/** The listings a live search found. */
public record ListingSearchResult(List<FoundListing> listings) {
}
