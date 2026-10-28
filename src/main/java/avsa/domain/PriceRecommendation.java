package avsa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The result of pricing (F03): wholesale and retail ranges, the asking price
 * and walk-away floor for the chosen strategy, and how they were derived.
 */
public final class PriceRecommendation {

    private final UUID id;
    private final StrategyType strategy;
    private final Money baseline;
    private final PriceRange retail;
    private final PriceRange wholesale;
    private final Money askingPrice;
    private final Money walkAwayFloor;
    private final Confidence confidence;
    private final List<PriceAdjustment> adjustments;
    private final List<String> assumptions;
    private final List<String> warnings;
    private final PricingParameters parameters;
    private final Instant createdAt;
    private String explanation;
    private boolean templateExplanation;
    private boolean stale;

    public PriceRecommendation(StrategyType strategy, Money baseline, PriceRange retail, PriceRange wholesale,
                               Money askingPrice, Money walkAwayFloor, Confidence confidence,
                               List<PriceAdjustment> adjustments, List<String> assumptions, List<String> warnings,
                               PricingParameters parameters, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.strategy = Objects.requireNonNull(strategy);
        this.baseline = Objects.requireNonNull(baseline);
        this.retail = Objects.requireNonNull(retail);
        this.wholesale = Objects.requireNonNull(wholesale);
        this.askingPrice = Objects.requireNonNull(askingPrice);
        this.walkAwayFloor = Objects.requireNonNull(walkAwayFloor);
        this.confidence = Objects.requireNonNull(confidence);
        this.adjustments = List.copyOf(adjustments);
        this.assumptions = List.copyOf(assumptions);
        this.warnings = List.copyOf(warnings);
        this.parameters = Objects.requireNonNull(parameters);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID id() { return id; }
    public StrategyType strategy() { return strategy; }
    public Money baseline() { return baseline; }
    public PriceRange retail() { return retail; }
    public PriceRange wholesale() { return wholesale; }
    public Money askingPrice() { return askingPrice; }
    public Money walkAwayFloor() { return walkAwayFloor; }
    public Confidence confidence() { return confidence; }
    public List<PriceAdjustment> adjustments() { return adjustments; }
    public List<String> assumptions() { return assumptions; }
    public List<String> warnings() { return warnings; }
    public PricingParameters parameters() { return parameters; }
    public Instant createdAt() { return createdAt; }
    public Optional<String> explanation() { return Optional.ofNullable(explanation); }
    public boolean templateExplanation() { return templateExplanation; }
    public boolean stale() { return stale; }

    public void setExplanation(String explanation, boolean fromTemplate) {
        this.explanation = explanation;
        this.templateExplanation = fromTemplate;
    }

    /** Called when new information (e.g. an imported history) makes this price out of date. */
    public void markStale() {
        this.stale = true;
    }
}
