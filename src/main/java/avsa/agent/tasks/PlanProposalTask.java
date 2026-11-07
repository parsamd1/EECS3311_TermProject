package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.agent.StepCatalog;
import avsa.domain.Platform;
import avsa.domain.StrategyType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Asks the AI to turn the seller's goal into a plan made of catalog steps (F12). */
public final class PlanProposalTask implements AiTask<PlanProposal> {

    private final String goal;
    private final String vehicle;
    private final StepCatalog catalog;

    /** @param vehicle a short description such as "2022 Mazda CX-5 GS, 49,000 km"; never the VIN */
    public PlanProposalTask(String goal, String vehicle, StepCatalog catalog) {
        this.goal = goal;
        this.vehicle = vehicle;
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "plan-proposal";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.planProposal(goal, vehicle, catalog);
    }

    @Override
    public Class<PlanProposal> resultType() {
        return PlanProposal.class;
    }

    @Override
    public List<String> validate(PlanProposal p) {
        if (p.asksQuestion() || p.outsideScope()) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        if (p.steps().isEmpty()) {
            errors.add("Give at least one step, or a question, or an outOfScope note.");
        }
        for (String step : p.steps()) {
            if (catalog.lookup(step).isEmpty()) {
                errors.add("Unknown step \"" + step + "\". Use only: " + catalog.steps() + ".");
            }
        }
        if (p.strategy() != null && Arrays.stream(StrategyType.values()).noneMatch(s -> s.name().equals(p.strategy()))) {
            errors.add("strategy must be MAX_PROFIT, QUICK_SALE or null.");
        }
        for (String platform : p.platforms()) {
            if (Arrays.stream(Platform.values()).noneMatch(x -> x.name().equals(platform))) {
                errors.add("Unknown platform \"" + platform + "\". Use AUTOTRADER, KIJIJI or FACEBOOK_MARKETPLACE.");
            }
        }
        return errors;
    }

    @Override
    public PlanProposal fallback() {
        return catalog.templatePlanFor(goal);
    }
}
