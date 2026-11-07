package avsa.application;

import avsa.domain.PlanStep;

/** How one plan step ended, after any retries (F12). */
public record StepResult(PlanStep step, boolean ok, String summary) {
}
