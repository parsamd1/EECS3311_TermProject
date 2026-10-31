package avsa.infrastructure.market;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/** The bundled listings, used directly and as the last-resort fallback. */
public final class SeededDatasetSource implements MarketDataSource {

    private final Path datasetPath;
    private final Clock clock;

    public SeededDatasetSource(Path datasetPath, Clock clock) {
        this.datasetPath = datasetPath;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "Seeded dataset";
    }

    @Override
    public boolean isAvailable() {
        return Files.isReadable(datasetPath);
    }

    @Override
    public MarketSearchResult search(MarketQuery query) {
        ListingCsvReader.Contents contents = ListingCsvReader.read(datasetPath);
        return new MarketSearchResult(name(), contents.listings().stream().filter(query::matches).toList(),
                contents.skippedRecords(), false, clock.instant());
    }
}
