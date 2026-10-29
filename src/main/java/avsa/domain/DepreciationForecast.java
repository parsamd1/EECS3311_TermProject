package avsa.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of F06: projected values at 3, 6 and 12 months and the break-even month. */
public final class DepreciationForecast {

    private final Money referencePrice;
    private final Segment segment;
    private final Money monthlyHoldingCost;
    private final Money wholesaleToday;
    private final List<ForecastPoint> points;
    private final Integer breakEvenMonth;
    private final List<String> warnings;

    public DepreciationForecast(Money referencePrice, Segment segment, Money monthlyHoldingCost, Money wholesaleToday,
                                List<ForecastPoint> points, Integer breakEvenMonth, List<String> warnings) {
        this.referencePrice = Objects.requireNonNull(referencePrice);
        this.segment = Objects.requireNonNull(segment);
        this.monthlyHoldingCost = Objects.requireNonNull(monthlyHoldingCost);
        this.wholesaleToday = Objects.requireNonNull(wholesaleToday);
        this.points = List.copyOf(points);
        this.breakEvenMonth = breakEvenMonth;
        this.warnings = List.copyOf(warnings);
    }

    public Money referencePrice() { return referencePrice; }
    public Segment segment() { return segment; }
    public Money monthlyHoldingCost() { return monthlyHoldingCost; }
    public Money wholesaleToday() { return wholesaleToday; }
    public List<ForecastPoint> points() { return points; }
    public List<String> warnings() { return warnings; }

    /** First month in which waiting for a retail buyer nets less than selling to a dealer today. */
    public Optional<Integer> breakEvenMonth() {
        return Optional.ofNullable(breakEvenMonth);
    }

    public Optional<ForecastPoint> pointAt(int months) {
        return points.stream().filter(p -> p.months() == months).findFirst();
    }
}
