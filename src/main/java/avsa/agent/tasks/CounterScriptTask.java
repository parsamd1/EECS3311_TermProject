package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.ComparableListing;
import avsa.domain.Money;
import avsa.domain.OfferEvaluation;
import avsa.domain.OfferVerdict;
import avsa.domain.PriceRecommendation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the AI for what the seller can say back to a buyer (F11). The verdict and counter amount
 * come from fixed rules; the script may only quote those figures and the comparables' prices.
 */
public final class CounterScriptTask implements AiTask<String> {

    private static final Pattern DOLLARS = Pattern.compile("\\$\\s?(\\d[\\d,]*)");
    private static final double TOLERANCE = 100;
    static final int MAX_LENGTH = 1_200;

    private final OfferEvaluation evaluation;
    private final PriceRecommendation price;
    private final List<ComparableListing> comparables;

    public CounterScriptTask(OfferEvaluation evaluation, PriceRecommendation price, List<ComparableListing> comparables) {
        this.evaluation = evaluation;
        this.price = price;
        this.comparables = List.copyOf(comparables);
    }

    @Override
    public String name() {
        return "counter-script";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.counterScript(evaluation, price, comparables);
    }

    @Override
    public Class<String> resultType() {
        return String.class;
    }

    @Override
    public List<String> validate(String script) {
        List<String> problems = new ArrayList<>();
        if (script.length() > MAX_LENGTH) {
            problems.add("Keep the script under " + MAX_LENGTH + " characters.");
        }
        evaluation.counterAmount().ifPresent(counter -> {
            if (!mentions(script, counter)) {
                problems.add("The script must ask for the counter amount of " + counter + ".");
            }
        });
        List<Money> known = new ArrayList<>(List.of(evaluation.offer().amount(), price.wholesale().low(),
                price.wholesale().high(), price.retail().low(), price.retail().high(), price.askingPrice()));
        evaluation.counterAmount().ifPresent(known::add);
        comparables.forEach(c -> known.add(c.askingPrice()));
        Matcher m = DOLLARS.matcher(script);
        while (m.find()) {
            double amount = Double.parseDouble(m.group(1).replace(",", ""));
            if (known.stream().noneMatch(k -> Math.abs(k.toDouble() - amount) <= TOLERANCE)) {
                problems.add("The script states " + m.group() + ", which is not one of the figures given.");
            }
        }
        return problems;
    }

    private static boolean mentions(String script, Money amount) {
        Matcher m = DOLLARS.matcher(script);
        while (m.find()) {
            if (Math.abs(Double.parseDouble(m.group(1).replace(",", "")) - amount.toDouble()) <= 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String fallback() {
        return template(evaluation, price);
    }

    public static String template(OfferEvaluation e, PriceRecommendation price) {
        String thanks = "Thank you for your offer of " + e.offer().amount() + ". ";
        if (e.verdict() == OfferVerdict.STRONG) {
            return thanks + "That works for me. Let's arrange a time to complete the paperwork.";
        }
        return thanks + "Comparable vehicles are listed between " + price.retail().low() + " and " + price.retail().high()
                + ", and dealers typically pay up to " + price.wholesale().high() + " for this vehicle. I can accept "
                + e.counterAmount().orElse(price.wholesale().high()) + ".";
    }
}
