package avsa.agent;

import avsa.domain.Plan;
import avsa.domain.PlanStep;
import avsa.domain.StepType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Makes any proposed plan safe to run (F12): only registered steps, each once, with its prerequisites, in
 * the catalog's order, with the catalog's checkpoints, and no more than the plan's maximum step count.
 * Every change is recorded in the plan's notes so the seller can see what the program adjusted.
 */
public final class PlanValidator {

    private final StepCatalog catalog;

    public PlanValidator(StepCatalog catalog) {
        this.catalog = catalog;
    }

    public Plan validate(Plan draft) {
        List<String> notes = new ArrayList<>(draft.notes());
        Set<StepType> wanted = EnumSet.noneOf(StepType.class);
        for (PlanStep step : draft.steps()) {
            if (catalog.isRegistered(step.type())) {
                wanted.add(step.type());
            } else {
                notes.add("Dropped a step that is not in the catalog: " + step.type() + ".");
            }
        }
        for (StepType type : List.copyOf(wanted)) {
            addPrerequisites(type, wanted, notes);
        }
        if (wanted.contains(StepType.EXPORT_LISTING) && draft.platforms().isEmpty()) {
            wanted.remove(StepType.EXPORT_LISTING);
            notes.add("No marketplace was named, so the listing is written but not formatted for one.");
        }
        List<PlanStep> steps = new ArrayList<>();
        for (StepType type : catalog.steps()) {
            if (wanted.contains(type)) {
                steps.add(new PlanStep(type, catalog.isCheckpoint(type)));
            }
        }
        if (steps.size() > draft.maxSteps()) {
            notes.add("The plan was cut to " + draft.maxSteps() + " steps.");
            steps = new ArrayList<>(steps.subList(0, draft.maxSteps()));
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("The plan has no steps the assistant can run.");
        }
        return new Plan(draft.id() == null ? UUID.randomUUID() : draft.id(), draft.goal(), draft.maxSteps(),
                draft.autoApprove(), draft.templatePlan(), draft.strategy(), draft.liveSearch(), draft.platforms(),
                steps, notes);
    }

    private void addPrerequisites(StepType type, Set<StepType> wanted, List<String> notes) {
        for (StepType needed : catalog.prerequisitesOf(type)) {
            if (wanted.add(needed)) {
                notes.add("Added " + label(needed) + " because " + label(type) + " needs it.");
            }
            addPrerequisites(needed, wanted, notes);
        }
    }

    private static String label(StepType type) {
        return type.name().toLowerCase().replace('_', ' ');
    }
}
