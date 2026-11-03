package avsa.tools;

import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.PriceRecommendation;
import avsa.domain.StrategyType;
import avsa.services.PricingEngine;
import avsa.services.PricingInput;
import avsa.services.PricingStrategy;

/**
 * Computes the price with the chosen strategy (F03). The AI explanation is added with the
 * pricing AI work; until then a plain template explains the numbers. Args: input, strategy.
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
        rec.setExplanation(templateExplanation(rec), true);
        return ToolResult.success(rec);
    }

    static String templateExplanation(PriceRecommendation rec) {
        return "The retail range " + rec.retail() + " is what private buyers are expected to pay, based on the "
                + "comparable listings after adjusting for odometer, condition and history. Dealers usually pay about "
                + Math.round(rec.parameters().dealerSpread() * 100) + "% less, which gives the wholesale range "
                + rec.wholesale() + ". Ask " + rec.askingPrice() + " and do not accept less than " + rec.walkAwayFloor()
                + ". Confidence is " + rec.confidence().name().toLowerCase() + ".";
    }
}
