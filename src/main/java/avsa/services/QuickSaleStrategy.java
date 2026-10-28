package avsa.services;

import avsa.domain.Money;
import avsa.domain.PriceRange;
import avsa.domain.StrategyType;

/**
 * Sell to a dealer now. Ask for the top of the wholesale range and walk away
 * below its bottom.
 */
public final class QuickSaleStrategy implements PricingStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.QUICK_SALE;
    }

    @Override
    public Money askingPrice(PriceRange wholesale, PriceRange retail, double negotiationMargin) {
        return wholesale.high();
    }

    @Override
    public Money walkAwayFloor(PriceRange wholesale, PriceRange retail) {
        return wholesale.low();
    }
}
