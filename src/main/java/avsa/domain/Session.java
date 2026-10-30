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
    private final VehicleProfile profile;
    private Instant lastModified;
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean readOnly;
    private VehicleHistory history;
    private List<PriceRecommendation> priceHistory = new ArrayList<>();
    private UUID activePriceId;
    private DepreciationForecast forecast;
    private List<OfferEvaluation> offerLog = new ArrayList<>();

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

    public Optional<PriceRecommendation> activePrice() {
        return priceHistory.stream().filter(p -> p.id().equals(activePriceId)).findFirst();
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

    /** A copy under a new id and name that shares this session's results. */
    public Session copyAs(UUID newId, String newName, Instant when) {
        Session copy = new Session(newId, newName, profile, when);
        copy.history = history;
        copy.priceHistory = new ArrayList<>(priceHistory);
        copy.activePriceId = activePriceId;
        copy.forecast = forecast;
        copy.offerLog = new ArrayList<>(offerLog);
        return copy;
    }
}
