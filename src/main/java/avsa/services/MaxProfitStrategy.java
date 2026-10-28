package avsa.services;

import avsa.domain.Money;
import avsa.domain.PriceRange;
import avsa.domain.StrategyType;

/**
 * Sell to a private buyer at retail. The asking price leaves negotiation room
 * above the top of the expected retail range, and the floor is the top of the
 * wholesale range: no private offer should be accepted below what a dealer pays.
 */
public final class MaxProfitStrategy implements PricingStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.MAX_PROFIT;
    }

    @Override
    public Money askingPrice(PriceRange wholesale, PriceRange retail, double negotiationMargin) {
        return retail.high().times(1 / (1 - negotiationMargin)).roundTo(100);
    }

    @Override
    public Money walkAwayFloor(PriceRange wholesale, PriceRange retail) {
        return wholesale.high();
    }
}
