package avsa.services;

import avsa.domain.Money;
import avsa.domain.Segment;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Inputs for F06. {@code segment} may be null when it could not be detected;
 * the default (economy) rates are then used with a warning.
 */
public record ForecastInput(Money referencePrice, int modelYear, Segment segment, LocalDate today,
                            Money monthlyHoldingCost, Money wholesaleToday) {

    public ForecastInput {
        Objects.requireNonNull(referencePrice, "referencePrice");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(wholesaleToday, "wholesaleToday");
        monthlyHoldingCost = monthlyHoldingCost == null ? Money.ZERO : monthlyHoldingCost;
    }
}
