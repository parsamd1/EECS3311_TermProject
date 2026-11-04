package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.AdjustmentKind;
import avsa.domain.Money;
import avsa.domain.PriceAdjustment;
import avsa.domain.PriceRecommendation;
import avsa.domain.VehicleFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the AI to explain a computed price and suggest adjustments only it can judge, such as
 * included winter tires (F03). The AI cannot change the numbers: any dollar amount it writes
 * must be one the engine computed, and each proposal is capped.
 */
public final class PriceExplanationTask implements AiTask<PriceExplanation> {

    private static final Pattern DOLLARS = Pattern.compile("\\$\\s?(\\d[\\d,]*)");
    /** Rounding in the text is fine; anything further off is a number the engine did not produce. */
    private static final double TOLERANCE = 100;
    private static final Pattern ALREADY_COUNTED = Pattern.compile(
            "odometer|mileage|kilomet|\\bkm\\b|scratch|dent|rust|defect|accident|owner|history|condition",
            Pattern.CASE_INSENSITIVE);

    private final PriceRecommendation rec;
    private final VehicleFacts facts;
    private final int comparableCount;

    public PriceExplanationTask(PriceRecommendation rec, VehicleFacts facts, int comparableCount) {
        this.rec = rec;
        this.facts = facts;
        this.comparableCount = comparableCount;
    }

    @Override
    public String name() {
        return "price-explanation";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.pricingExplanation(rec, facts, comparableCount);
    }

    @Override
    public Class<PriceExplanation> resultType() {
        return PriceExplanation.class;
    }

    @Override
    public List<String> validate(PriceExplanation result) {
        List<String> problems = new ArrayList<>();
        if (result.explanation() == null || result.explanation().isBlank()) {
            problems.add("The explanation is empty.");
        } else {
            problems.addAll(unknownAmounts(result.explanation()));
        }
        List<ProposedAdjustment> adjustments = result.adjustments() == null ? List.of() : result.adjustments();
        int max = rec.parameters().maxAiAdjustments();
        double capPercent = rec.parameters().aiAdjustmentCap() * 100;
        if (adjustments.size() > max) {
            problems.add("Propose at most " + max + " adjustments.");
        }
        for (ProposedAdjustment a : adjustments) {
            if (a.label() == null || a.label().isBlank() || a.percent() == null) {
                problems.add("Every adjustment needs a label and a percent.");
            } else if (Math.abs(a.percent()) > capPercent) {
                problems.add("The adjustment \"" + a.label() + "\" is " + a.percent() + "%; the limit is " + capPercent
                        + "% either way.");
            } else if (ALREADY_COUNTED.matcher(a.label()).find()) {
                problems.add("The adjustment \"" + a.label() + "\" repeats something the price already accounts for "
                        + "(odometer, condition or history).");
            }
        }
        return problems;
    }

    private List<String> unknownAmounts(String text) {
        List<Money> known = new ArrayList<>(List.of(rec.baseline(), rec.retail().low(), rec.retail().high(),
                rec.wholesale().low(), rec.wholesale().high(), rec.askingPrice(), rec.walkAwayFloor()));
        rec.adjustments().forEach(a -> known.add(a.amount()));
        List<String> problems = new ArrayList<>();
        Matcher m = DOLLARS.matcher(text);
        while (m.find()) {
            double amount = Double.parseDouble(m.group(1).replace(",", ""));
            boolean matches = known.stream().anyMatch(k -> Math.abs(Math.abs(k.toDouble()) - amount) <= TOLERANCE);
            if (!matches) {
                problems.add("The explanation states " + m.group() + ", which is not one of the computed figures. "
                        + "Use only the figures given.");
            }
        }
        return problems;
    }

    /** Without the AI the numbers are explained by a template and nothing is proposed. */
    @Override
    public PriceExplanation fallback() {
        return new PriceExplanation(templateExplanation(rec), List.of());
    }

    public static String templateExplanation(PriceRecommendation rec) {
        return "The retail range " + rec.retail() + " is what private buyers are expected to pay, based on the "
                + "comparable listings after adjusting for odometer, condition and history. Dealers usually pay about "
                + Math.round(rec.parameters().dealerSpread() * 100) + "% less, which gives the wholesale range "
                + rec.wholesale() + ". Ask " + rec.askingPrice() + " and do not accept less than " + rec.walkAwayFloor()
                + ". Confidence is " + rec.confidence().name().toLowerCase(Locale.ROOT) + ".";
    }

    /** The proposals as adjustments that do not count until the seller accepts them. */
    public static List<PriceAdjustment> proposals(PriceExplanation explanation, Money baseline) {
        List<PriceAdjustment> result = new ArrayList<>();
        for (ProposedAdjustment a : explanation.adjustments() == null ? List.<ProposedAdjustment>of()
                : explanation.adjustments()) {
            String label = a.reason() == null || a.reason().isBlank() ? a.label() : a.label() + ": " + a.reason();
            result.add(new PriceAdjustment(UUID.randomUUID(), label, baseline.times(a.percent() / 100).roundTo(50),
                    AdjustmentKind.AI_PROPOSED, false));
        }
        return result;
    }
}
