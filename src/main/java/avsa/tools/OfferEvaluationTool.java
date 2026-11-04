package avsa.tools;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.agent.tasks.CounterScriptTask;
import avsa.domain.ComparableListing;
import avsa.domain.DepreciationForecast;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.PriceRecommendation;
import avsa.services.OfferEvaluator;
import java.util.List;

/**
 * Judges an offer with fixed rules, then has the AI write the reply (F11).
 * Args: offer, price, forecast (optional), comparables (optional).
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
        PriceRecommendation price = args.require("price", PriceRecommendation.class);
        OfferEvaluation evaluation = evaluator.evaluate(args.require("offer", Offer.class), price,
                args.optional("forecast", DepreciationForecast.class).orElse(null));
        @SuppressWarnings("unchecked")
        List<ComparableListing> comps = args.optional("comparables", List.class).orElse(List.of());
        AiResult<String> script = ai.run(new CounterScriptTask(evaluation, price, comps));
        evaluation.setScript(script.value(), script.usedFallback());
        return ToolResult.success(evaluation, script.usedFallback()
                ? List.of("The AI script was unavailable, so a template script is shown.") : List.of());
    }
}
