package avsa.agent.tasks;

import avsa.domain.BuyerIntent;

/**
 * One simulated buyer message as the model returned it.
 *
 * @param listingGap a question the listing does not answer, or null
 */
public record BuyerTurn(String message, Integer offerAmount, BuyerIntent intent, String listingGap) {
}
