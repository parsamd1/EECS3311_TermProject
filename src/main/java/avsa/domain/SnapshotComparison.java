package avsa.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** How the market changed between two snapshots (F10). Listings are identified by their ids. */
public record SnapshotComparison(MarketSnapshot first, MarketSnapshot second, Money medianChange, int countChange,
                                 List<String> addedListings, List<String> removedListings) {

    public static SnapshotComparison of(MarketSnapshot first, MarketSnapshot second) {
        Set<String> before = ids(first);
        Set<String> after = ids(second);
        return new SnapshotComparison(first, second, second.stats().median().minus(first.stats().median()),
                second.stats().count() - first.stats().count(),
                after.stream().filter(id -> !before.contains(id)).sorted().toList(),
                before.stream().filter(id -> !after.contains(id)).sorted().toList());
    }

    private static Set<String> ids(MarketSnapshot snapshot) {
        return snapshot.rankedListings().stream().map(ComparableListing::listingId).collect(Collectors.toSet());
    }
}
