package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.Claim;
import avsa.domain.ComparableListing;
import avsa.domain.PercentileMetric;
import avsa.domain.PriceRecommendation;
import avsa.domain.VehicleFacts;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks the AI to turn the metrics and top comparables into advantages, weaknesses, remedies and
 * talking points (F05). Comparables are labelled C1, C2... in the prompt and mapped back to their ids.
 */
public final class PositioningTask implements AiTask<PositioningDraft> {

    static final int TOP_COMPARABLES = 5;

    private final List<PercentileMetric> metrics;
    private final List<ComparableListing> top;
    private final VehicleFacts facts;
    private final PriceRecommendation price;

    public PositioningTask(List<PercentileMetric> metrics, List<ComparableListing> comps, VehicleFacts facts,
                           PriceRecommendation price) {
        this.metrics = List.copyOf(metrics);
        this.top = comps.stream().filter(c -> !c.excluded()).limit(TOP_COMPARABLES).toList();
        this.facts = facts;
        this.price = price;
    }

    @Override
    public String name() {
        return "positioning";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.positioning(metrics, top, facts, price);
    }

    @Override
    public Class<PositioningDraft> resultType() {
        return PositioningDraft.class;
    }

    @Override
    public List<String> validate(PositioningDraft draft) {
        if (draft.claims() == null || draft.claims().isEmpty()) {
            return List.of("Give at least one claim.");
        }
        return draft.claims().stream().anyMatch(c -> c.kind() == null || c.text() == null)
                ? List.of("Every claim needs a kind and a text.") : List.of();
    }

    /** With no AI there are no claims; the caller shows the metrics only. */
    @Override
    public PositioningDraft fallback() {
        return new PositioningDraft(List.of(), "");
    }

    /** The claims with C1, C2... replaced by listing ids; unknown labels are kept so the validator removes them. */
    public List<Claim> claims(PositioningDraft draft) {
        List<Claim> claims = new ArrayList<>();
        for (PositioningDraft.DraftClaim c : draft.claims()) {
            List<String> refs = new ArrayList<>();
            for (String ref : c.evidence() == null ? List.<String>of() : c.evidence()) {
                String r = ref.strip();
                if (r.matches("(?i)C\\d+")) {
                    int index = Integer.parseInt(r.substring(1)) - 1;
                    refs.add(index >= 0 && index < top.size() ? top.get(index).listingId() : "unknown:" + r);
                } else {
                    refs.add(r);
                }
            }
            claims.add(new Claim(c.kind(), c.text(), refs));
        }
        return claims;
    }
}
