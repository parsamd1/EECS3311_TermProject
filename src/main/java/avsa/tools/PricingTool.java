package avsa.tools;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.agent.tasks.PriceExplanation;
import avsa.agent.tasks.PriceExplanationTask;
import avsa.domain.PriceRecommendation;
import avsa.domain.StrategyType;
import avsa.domain.VehicleFacts;
import avsa.services.PricingEngine;
import avsa.services.PricingInput;
import avsa.services.PricingStrategy;
import java.util.List;

/**
 * Computes the price with the chosen strategy (F03), then has the AI explain it and propose
 * bounded adjustments. Args: input, strategy, explain (default true; false when re-pricing with
 * accepted adjustments).
 */
public final class PricingTool extends AiAssistedTool {

    public static final String NAME = "pricing";

    private final PricingEngine engine;

    public PricingTool(AiTaskRunner ai, PricingEngine engine) {
        super(ai);
        this.engine = engine;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        PricingInput input = args.require("input", PricingInput.class);
        StrategyType strategy = args.require("strategy", StrategyType.class);
        PriceRecommendation rec = engine.recommend(input, PricingStrategy.of(strategy));
        if (!args.optional("explain", Boolean.class).orElse(true)) {
            rec.setExplanation(PriceExplanationTask.templateExplanation(rec), true);
            return ToolResult.success(rec);
        }
        VehicleFacts facts = VehicleFacts.from(input.profile(), input.history(), rec.askingPrice());
        AiResult<PriceExplanation> result = ai.run(new PriceExplanationTask(rec, facts,
                (int) input.comparables().stream().filter(c -> !c.excluded()).count()));
        rec.setExplanation(result.value().explanation(), result.usedFallback());
        rec.setProposals(PriceExplanationTask.proposals(result.value(), rec.baseline()));
        return ToolResult.success(rec, result.usedFallback()
                ? List.of("The AI explanation was unavailable, so a template explanation is shown.") : List.of());
    }
}
