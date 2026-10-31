package avsa.domain;

import java.util.Comparator;
import java.util.List;

/** Count, minimum, median and maximum asking price of a set of listings. */
public record MarketStats(int count, Money min, Money median, Money max) {

    public static MarketStats of(List<ComparableListing> listings) {
        List<Money> prices = listings.stream().map(ComparableListing::askingPrice)
                .sorted(Comparator.naturalOrder()).toList();
        if (prices.isEmpty()) {
            return new MarketStats(0, Money.ZERO, Money.ZERO, Money.ZERO);
        }
        int n = prices.size();
        Money median = n % 2 == 1 ? prices.get(n / 2) : prices.get(n / 2 - 1).plus(prices.get(n / 2)).times(0.5);
        return new MarketStats(n, prices.get(0), median, prices.get(n - 1));
    }
}
