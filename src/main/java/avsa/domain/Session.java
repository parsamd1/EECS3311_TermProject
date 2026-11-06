package avsa.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything saved for one vehicle (F10). This is the unit that is persisted;
 * each feature stores its results here through the controller.
 */
public final class Session {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final UUID id;
    private String name;
    private VehicleProfile profile;
    private Instant lastModified;
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean readOnly;
    private VehicleHistory history;
    private List<PriceRecommendation> priceHistory = new ArrayList<>();
    private UUID activePriceId;
    private DepreciationForecast forecast;
    private List<OfferEvaluation> offerLog = new ArrayList<>();
    private List<MarketSnapshot> snapshots = new ArrayList<>();
    private List<GeneratedListing> listings = new ArrayList<>();
    private PositioningReport positioning;
    private List<SimulationSession> simulations = new ArrayList<>();

    public Session(UUID id, String name, VehicleProfile profile, Instant lastModified) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.profile = Objects.requireNonNull(profile);
        this.lastModified = Objects.requireNonNull(lastModified);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public VehicleProfile activeProfile() { return profile; }
    public Instant lastModified() { return lastModified; }
    public int schemaVersion() { return schemaVersion; }
    public boolean readOnly() { return readOnly; }
    public Optional<VehicleHistory> history() { return Optional.ofNullable(history); }
    public List<PriceRecommendation> priceHistory() { return List.copyOf(priceHistory); }
    public Optional<DepreciationForecast> forecast() { return Optional.ofNullable(forecast); }
    public List<OfferEvaluation> offerLog() { return List.copyOf(offerLog); }
    public List<MarketSnapshot> snapshots() { return List.copyOf(snapshots); }
    public List<GeneratedListing> listings() { return List.copyOf(listings); }
    public Optional<PositioningReport> positioning() { return Optional.ofNullable(positioning); }
    public void setPositioning(PositioningReport report) { this.positioning = report; }
    public List<SimulationSession> simulations() { return List.copyOf(simulations); }
    public void addSimulation(SimulationSession simulation) { simulations.add(simulation); }

    public Optional<SimulationSession> latestSimulation() {
        return simulations.isEmpty() ? Optional.empty() : Optional.of(simulations.get(simulations.size() - 1));
    }

    public Optional<GeneratedListing> latestListing() {
        return listings.isEmpty() ? Optional.empty() : Optional.of(listings.get(listings.size() - 1));
    }

    public Optional<MarketSnapshot> latestSnapshot() {
        return snapshots.isEmpty() ? Optional.empty() : Optional.of(snapshots.get(snapshots.size() - 1));
    }

    public Optional<PriceRecommendation> activePrice() {
        return priceHistory.stream().filter(p -> p.id().equals(activePriceId)).findFirst();
    }

    /** A changed profile makes the active price out of date (F01, F03). */
    public void updateProfile(VehicleProfile newProfile) {
        this.profile = Objects.requireNonNull(newProfile);
        activePrice().ifPresent(PriceRecommendation::markStale);
    }

    public void rename(String newName) { this.name = Objects.requireNonNull(newName); }
    public void touch(Instant when) { this.lastModified = when; }
    public void markReadOnly() { this.readOnly = true; }

    /** Stores the history; any active price no longer reflects it, so it becomes stale (F04). */
    public void setHistory(VehicleHistory newHistory) {
        this.history = newHistory;
        activePrice().ifPresent(PriceRecommendation::markStale);
    }

    public void setActivePrice(PriceRecommendation rec) {
        priceHistory.add(rec);
        activePriceId = rec.id();
    }

    public void setForecast(DepreciationForecast newForecast) { this.forecast = newForecast; }
    public void addOffer(OfferEvaluation evaluation) { offerLog.add(evaluation); }
    public void addSnapshot(MarketSnapshot snapshot) { snapshots.add(snapshot); }

    /** The seller pinned or excluded a comparable: the latest snapshot is re-ranked and the active price is out of date (F02). */
    public void replaceLatestSnapshot(MarketSnapshot snapshot) {
        if (snapshots.isEmpty()) {
            throw new IllegalStateException("No market snapshot to replace.");
        }
        snapshots.set(snapshots.size() - 1, snapshot);
        activePrice().ifPresent(PriceRecommendation::markStale);
    }
    public void addListing(GeneratedListing listing) { listings.add(listing); }

    /** A copy under a new id and name that shares this session's results. */
    public Session copyAs(UUID newId, String newName, Instant when) {
        Session copy = new Session(newId, newName, profile, when);
        copy.history = history;
        copy.priceHistory = new ArrayList<>(priceHistory);
        copy.activePriceId = activePriceId;
        copy.forecast = forecast;
        copy.offerLog = new ArrayList<>(offerLog);
        copy.snapshots = new ArrayList<>(snapshots);
        copy.listings = new ArrayList<>(listings);
        copy.positioning = positioning;
        copy.simulations = new ArrayList<>(simulations);
        return copy;
    }
}
