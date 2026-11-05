package avsa.tools;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.agent.tasks.PositioningDraft;
import avsa.agent.tasks.PositioningTask;
import avsa.domain.ComparableListing;
import avsa.domain.PercentileMetric;
import avsa.domain.PositioningReport;
import avsa.domain.PriceRecommendation;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleProfile;
import avsa.services.ClaimValidator;
import avsa.services.PositioningAnalyzer;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares the vehicle with its competition (F05): metrics by code, claims by the AI, and only the
 * claims the evidence supports are kept. Args: profile, facts, price, comparables.
 */
public final class PositioningTool extends AiAssistedTool {

    public static final String NAME = "positioning";
    static final int MIN_COMPARABLES = 3;

    private final PositioningAnalyzer analyzer;
    private final ClaimValidator validator;

    public PositioningTool(AiTaskRunner ai, PositioningAnalyzer analyzer, ClaimValidator validator) {
        super(ai);
        this.analyzer = analyzer;
        this.validator = validator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        VehicleProfile profile = args.require("profile", VehicleProfile.class);
        VehicleFacts facts = args.require("facts", VehicleFacts.class);
        PriceRecommendation price = args.require("price", PriceRecommendation.class);
        @SuppressWarnings("unchecked")
        List<ComparableListing> comps = args.require("comparables", List.class);

        List<PercentileMetric> metrics = analyzer.metrics(profile, price.askingPrice(), comps);
        List<String> notices = new ArrayList<>();
        if (comps.stream().filter(c -> !c.excluded()).count() < MIN_COMPARABLES) {
            notices.add("Fewer than " + MIN_COMPARABLES + " comparables: this is a limited report.");
        }
        PositioningTask task = new PositioningTask(metrics, comps, facts, price);
        AiResult<PositioningDraft> result = ai.run(task);
        if (result.usedFallback()) {
            notices.add("The AI analysis was unavailable, so only the metrics are shown.");
            return ToolResult.success(new PositioningReport(metrics, List.of(), "", 0, true, notices), notices);
        }
        PositioningReport draft = new PositioningReport(metrics, task.claims(result.value()),
                result.value().priceJustification(), 0, false, notices);
        PositioningReport checked = validator.filter(draft, comps, profile);
        if (checked.removedClaimCount() > 0) {
            notices.add(checked.removedClaimCount() + " AI claim(s) were removed because the evidence did not "
                    + "support them.");
        }
        return ToolResult.success(new PositioningReport(checked.metrics(), checked.claims(),
                checked.priceJustification(), checked.removedClaimCount(), false, notices), notices);
    }
}
