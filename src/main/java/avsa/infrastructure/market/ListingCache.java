package avsa.infrastructure.market;

import avsa.infrastructure.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The latest successful result per query. With a file it survives restarts, so when a source
 * fails, even in a new run of the app, the last real result is still available with its date.
 */
public final class ListingCache {

    /** How the cache is written to disk; a map keyed by a record does not serialize directly. */
    record Entry(MarketQuery query, MarketSearchResult result) {
    }

    private final Map<MarketQuery, MarketSearchResult> latest = new HashMap<>();
    private final Path file;
    private final JsonMapper mapper;

    /** A cache that lasts only while the app runs. */
    public ListingCache() {
        this(null, null);
    }

    /** A cache kept in {@code file}; a missing or unreadable file starts it empty. */
    public ListingCache(Path file, JsonMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        if (file != null && Files.isReadable(file)) {
            try {
                for (Entry entry : mapper.read(file, Entry[].class)) {
                    latest.put(entry.query(), entry.result());
                }
            } catch (IOException | RuntimeException e) {
                latest.clear();
            }
        }
    }

    public void put(MarketQuery query, MarketSearchResult result) {
        latest.put(query, result);
        save();
    }

    public Optional<MarketSearchResult> latest(MarketQuery query) {
        return Optional.ofNullable(latest.get(query));
    }

    /** A failed write only loses the copy on disk; the cache still works for this run. */
    private void save() {
        if (file == null) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        latest.forEach((query, result) -> entries.add(new Entry(query, result)));
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "listings", ".tmp");
            Files.write(temp, mapper.toBytes(entries));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Kept in memory; the next successful search tries to write it again.
        }
    }
}
