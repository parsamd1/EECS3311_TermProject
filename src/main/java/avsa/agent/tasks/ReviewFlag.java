package avsa.agent.tasks;

import avsa.domain.FlagCode;

/** One problem the review found in one listing. An unknown code fails parsing, so the model must use the fixed list. */
public record ReviewFlag(String listingId, FlagCode code, String reason) {
}
