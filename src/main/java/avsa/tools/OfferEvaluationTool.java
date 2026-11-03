package avsa.tools;

import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.DepreciationForecast;
import avsa.domain.Offer;
import avsa.domain.PriceRecommendation;
import avsa.services.OfferEvaluator;

/**
 * Judges an offer with fixed rules (F11). The AI counter-offer script is added with the offer
 * AI work. Args: offer, price, forecast (optional).
 */
public final class OfferEvaluationTool extends AiAssistedTool {

    public static final String NAME = "offer_evaluation";

    private final OfferEvaluator evaluator;

    public OfferEvaluationTool(AiTaskRunner ai, OfferEvaluator evaluator) {
        super(ai);
        this.evaluator = evaluator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        return ToolResult.success(evaluator.evaluate(args.require("offer", Offer.class),
                args.require("price", PriceRecommendation.class),
                args.optional("forecast", DepreciationForecast.class).orElse(null)));
    }
}
