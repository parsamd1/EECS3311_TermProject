package avsa.application;

import avsa.domain.Plan;
import avsa.domain.PlanStep;
import avsa.domain.SalesPackage;

/**
 * What the plan executor needs from the facade: run one step through the same feature methods the seller
 * uses by hand, apply an approval, and assemble the final result (F12).
 */
interface StepRunner {

    /** Runs the step and returns a one-line summary of its result. */
    String run(Plan plan, PlanStep step);

    /** The seller approved this checkpoint step's result, for example the recommended price. */
    void approved(Plan plan, PlanStep step);

    SalesPackage finish(Plan plan);
}
