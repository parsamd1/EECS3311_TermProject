package avsa.services;

import avsa.domain.DepreciationForecast;
import avsa.domain.ForecastPoint;
import avsa.domain.Money;
import avsa.domain.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic depreciation and cost-of-waiting forecast (F06). The same input
 * always produces the same output; no AI is involved.
 */
public final class DepreciationCalculator {

    static final int[] HORIZONS = {3, 6, 12};
    static final int MAX_MONTHS = 12;

    private final DepreciationRateTable rates;

    public DepreciationCalculator(DepreciationRateTable rates) {
        this.rates = rates;
    }

    /** Converts an annual rate to the equivalent monthly rate: {@code m = 1 - (1 - annual)^(1/12)}. */
    public static double monthlyRate(double annualRate) {
        return 1 - Math.pow(1 - annualRate, 1.0 / 12);
    }

    public DepreciationForecast forecast(ForecastInput input) {
        if (!input.referencePrice().isPositive() || !input.wholesaleToday().isPositive()) {
            throw new IllegalArgumentException("Reference price and wholesale value must be greater than zero.");
        }
        if (input.monthlyHoldingCost().amount().signum() < 0) {
            throw new IllegalArgumentException("Monthly holding cost cannot be negative.");
        }

        List<String> warnings = new ArrayList<>();
        rates.loadWarning().ifPresent(warnings::add);
        Segment segment = input.segment();
        if (segment == null) {
            segment = Segment.ECONOMY;
            warnings.add("Vehicle segment is unknown; default (economy) depreciation rates were used.");
        }
        int age = Math.max(0, input.today().getYear() - input.modelYear());
        if (!rates.covers(segment, age)) {
            warnings.add("The vehicle is older than the rate table covers; the oldest bracket was used and the "
                    + "estimate is unreliable.");
        }
        RateBand band = rates.bandFor(segment, age);
        double fastMonthly = monthlyRate(band.high());
        double slowMonthly = monthlyRate(band.low());

        Money p = input.referencePrice();
        List<ForecastPoint> points = new ArrayList<>();
        Integer breakEven = null;
        for (int t = 1; t <= MAX_MONTHS; t++) {
            ForecastPoint point = pointAt(t, p, fastMonthly, slowMonthly, input.monthlyHoldingCost());
            if (breakEven == null && point.netIfSoldThen().compareTo(input.wholesaleToday()) < 0) {
                breakEven = t;
            }
            for (int h : HORIZONS) {
                if (t == h) {
                    points.add(point);
                }
            }
        }
        return new DepreciationForecast(p, segment, input.monthlyHoldingCost(), input.wholesaleToday(),
                points, breakEven, warnings);
    }

    /** Projects {@code P x (1 - m)^t} at the fast (low value) and slow (high value) monthly rates. */
    static ForecastPoint pointAt(int months, Money p, double fastMonthly, double slowMonthly, Money monthlyHolding) {
        Money low = p.times(Math.pow(1 - fastMonthly, months));
        Money high = p.times(Math.pow(1 - slowMonthly, months));
        Money mid = low.plus(high).times(0.5);
        Money loss = p.minus(mid);
        double lossPercent = loss.toDouble() / p.toDouble() * 100;
        return new ForecastPoint(months, low, high, loss, Math.round(lossPercent * 10) / 10.0,
                monthlyHolding.times(months));
    }
}
