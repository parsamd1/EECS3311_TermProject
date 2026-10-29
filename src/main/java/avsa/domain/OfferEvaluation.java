package avsa.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The verdict on an offer (F11). The verdict, counter amount and comparison are
 * computed by fixed rules; only the counter-offer script is written by the LLM.
 */
public final class OfferEvaluation {

    private final Offer offer;
    private final OfferVerdict verdict;
    private final double positionInWholesale;
    private final Money gapToWholesaleTop;
    private final Money counterAmount;
    private final Money netIfWaiting;
    private final boolean belowWalkAwayFloor;
    private final boolean aboveRetailRange;
    private final List<String> warnings;
    private String script;
    private boolean templateScript;

    public OfferEvaluation(Offer offer, OfferVerdict verdict, double positionInWholesale, Money gapToWholesaleTop,
                           Money counterAmount, Money netIfWaiting, boolean belowWalkAwayFloor,
                           boolean aboveRetailRange, List<String> warnings) {
        this.offer = Objects.requireNonNull(offer);
        this.verdict = Objects.requireNonNull(verdict);
        this.positionInWholesale = positionInWholesale;
        this.gapToWholesaleTop = Objects.requireNonNull(gapToWholesaleTop);
        this.counterAmount = counterAmount;
        this.netIfWaiting = netIfWaiting;
        this.belowWalkAwayFloor = belowWalkAwayFloor;
        this.aboveRetailRange = aboveRetailRange;
        this.warnings = List.copyOf(warnings);
    }

    public Offer offer() { return offer; }
    public OfferVerdict verdict() { return verdict; }
    /** 0 at the bottom of the wholesale range, 1 at the top. */
    public double positionInWholesale() { return positionInWholesale; }
    /** Top of the wholesale range minus the offer; negative when the offer is above the range. */
    public Money gapToWholesaleTop() { return gapToWholesaleTop; }
    /** Empty when the recommendation is to accept. */
    public Optional<Money> counterAmount() { return Optional.ofNullable(counterAmount); }
    /** Expected net result of waiting for a retail sale instead; empty without a forecast. */
    public Optional<Money> netIfWaiting() { return Optional.ofNullable(netIfWaiting); }
    public boolean belowWalkAwayFloor() { return belowWalkAwayFloor; }
    /** The amount is above the retail range; the seller must confirm it was entered correctly. */
    public boolean aboveRetailRange() { return aboveRetailRange; }
    public List<String> warnings() { return warnings; }
    public Optional<String> script() { return Optional.ofNullable(script); }
    public boolean templateScript() { return templateScript; }

    public boolean recommendsAccepting() {
        return verdict == OfferVerdict.STRONG;
    }

    public void setScript(String script, boolean fromTemplate) {
        this.script = script;
        this.templateScript = fromTemplate;
    }
}
