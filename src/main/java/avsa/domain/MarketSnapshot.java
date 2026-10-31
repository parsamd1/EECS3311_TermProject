package avsa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The timestamped result of one comparable search (F02). */
public final class MarketSnapshot {

    private final UUID id;
    private final UUID profileId;
    private final Instant takenAt;
    private final String sourceName;
    private final boolean fromCache;
    private final Instant retrievedAt;
    private final int radiusKm;
    private final boolean lowConfidence;
    private final int skippedRecords;
    private final List<ComparableListing> listings;
    private final MarketStats stats;
    private final List<String> warnings;

    public MarketSnapshot(UUID id, UUID profileId, Instant takenAt, String sourceName, boolean fromCache,
                          Instant retrievedAt, int radiusKm, boolean lowConfidence, int skippedRecords,
                          List<ComparableListing> listings, MarketStats stats, List<String> warnings) {
        this.id = Objects.requireNonNull(id);
        this.profileId = Objects.requireNonNull(profileId);
        this.takenAt = Objects.requireNonNull(takenAt);
        this.sourceName = Objects.requireNonNull(sourceName);
        this.fromCache = fromCache;
        this.retrievedAt = Objects.requireNonNull(retrievedAt);
        this.radiusKm = radiusKm;
        this.lowConfidence = lowConfidence;
        this.skippedRecords = skippedRecords;
        this.listings = List.copyOf(listings);
        this.stats = Objects.requireNonNull(stats);
        this.warnings = List.copyOf(warnings);
    }

    public UUID id() { return id; }
    public UUID profileId() { return profileId; }
    public Instant takenAt() { return takenAt; }
    public String sourceName() { return sourceName; }
    public boolean fromCache() { return fromCache; }
    public Instant retrievedAt() { return retrievedAt; }
    public int radiusKm() { return radiusKm; }
    public boolean lowConfidence() { return lowConfidence; }
    public int skippedRecords() { return skippedRecords; }
    public List<ComparableListing> rankedListings() { return listings; }
    public MarketStats stats() { return stats; }
    public List<String> warnings() { return warnings; }

    /** The same search with its listings ranked again, after a review changed their scores. */
    public MarketSnapshot withListings(List<ComparableListing> ranked) {
        return new MarketSnapshot(id, profileId, takenAt, sourceName, fromCache, retrievedAt, radiusKm, lowConfidence,
                skippedRecords, ranked, MarketStats.of(ranked), warnings);
    }
}
