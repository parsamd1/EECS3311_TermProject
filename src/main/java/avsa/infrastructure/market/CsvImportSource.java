package avsa.infrastructure.market;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/** Listings from a CSV file the seller chose, in the format of data/listings-template.csv. */
public final class CsvImportSource implements MarketDataSource {

    private final Path csvPath;
    private final Clock clock;

    public CsvImportSource(Path csvPath, Clock clock) {
        this.csvPath = csvPath;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "Imported CSV";
    }

    @Override
    public boolean isAvailable() {
        return Files.isReadable(csvPath);
    }

    @Override
    public MarketSearchResult search(MarketQuery query) {
        ListingCsvReader.Contents contents = ListingCsvReader.read(csvPath);
        return new MarketSearchResult(name(), contents.listings().stream().filter(query::matches).toList(),
                contents.skippedRecords(), false, clock.instant());
    }
}
