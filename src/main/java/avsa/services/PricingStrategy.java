package avsa.services;

import avsa.domain.Money;
import avsa.domain.PriceRange;
import avsa.domain.StrategyType;

/**
 * Strategy pattern (F03): the two selling strategies share the whole pricing
 * pipeline and differ only in how they pick the asking price and walk-away floor.
 */
public interface PricingStrategy {

    StrategyType type();

    Money askingPrice(PriceRange wholesale, PriceRange retail, double negotiationMargin);

    Money walkAwayFloor(PriceRange wholesale, PriceRange retail);

    static PricingStrategy of(StrategyType type) {
        return switch (type) {
            case MAX_PROFIT -> new MaxProfitStrategy();
            case QUICK_SALE -> new QuickSaleStrategy();
        };
    }
}
