package avsa.infrastructure.market;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Builds the data source for the seller's choice, always behind the caching proxy with the
 * seeded dataset as the last fallback. One cache is shared, so a failed search can reuse
 * the last good result for the same query.
 */
public final class MarketSources {

    private final Path seededDataset;
    private final LiveSearchSource live;
    private final ListingCache cache;
    private final Clock clock;

    public MarketSources(Path seededDataset, LiveSearchSource live, ListingCache cache, Clock clock) {
        this.seededDataset = seededDataset;
        this.live = live;
        this.cache = cache;
        this.clock = clock;
    }

    /** @param csvFile needed only for {@link MarketSourceType#CSV} */
    public MarketDataSource forChoice(MarketSourceType type, Path csvFile) {
        SeededDatasetSource seeded = new SeededDatasetSource(seededDataset, clock);
        MarketDataSource real = switch (type) {
            case SEEDED -> seeded;
            case CSV -> {
                if (csvFile == null) {
                    throw new IllegalArgumentException("Choose the CSV file to import.");
                }
                yield new CsvImportSource(csvFile, clock);
            }
            case LIVE -> live;
        };
        return new CachingMarketDataSource(real, seeded, cache);
    }
}
