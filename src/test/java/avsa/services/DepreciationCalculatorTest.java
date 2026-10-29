package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.DepreciationForecast;
import avsa.domain.ForecastPoint;
import avsa.domain.Money;
import avsa.domain.Segment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DepreciationCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private final DepreciationCalculator calculator = new DepreciationCalculator(DepreciationRateTable.builtIn());

    /** The author's car: 2022 Mazda CX-5 (SUV, age 4: 7-11% a year), retail ~$28.5k, dealers ~$24.5k. */
    private static ForecastInput cx5(long holdingPerMonth) {
        return new ForecastInput(Money.cad(28_500), 2022, Segment.SUV, TODAY, Money.cad(holdingPerMonth),
                Money.cad(24_500));
    }

    @Test
    void monthlyRateCompoundsBackToTheAnnualRate() {
        double m = DepreciationCalculator.monthlyRate(0.12);
        assertEquals(0.88, Math.pow(1 - m, 12), 1e-12);
        assertEquals(0.0, DepreciationCalculator.monthlyRate(0.0), 1e-15);
    }

    @Test
    void projectsThreeSixAndTwelveMonths() {
        DepreciationForecast f = calculator.forecast(cx5(0));
        assertEquals(List.of(3, 6, 12), f.points().stream().map(ForecastPoint::months).toList());
        ForecastPoint year = f.pointAt(12).orElseThrow();
        assertEquals(Money.cad(25_365), year.lowValue(), "28,500 x (1 - 0.11)");
        assertEquals(Money.cad(26_505), year.highValue(), "28,500 x (1 - 0.07)");
        assertEquals(Money.cad(2_565), year.lossDollars(), "loss at the middle of the band");
        assertEquals(9.0, year.lossPercent());
        assertTrue(f.warnings().isEmpty());
    }

    @Test
    void valuesFallMonotonically() {
        DepreciationForecast f = calculator.forecast(cx5(0));
        ForecastPoint three = f.pointAt(3).orElseThrow();
        ForecastPoint six = f.pointAt(6).orElseThrow();
        ForecastPoint twelve = f.pointAt(12).orElseThrow();
        assertTrue(three.midValue().compareTo(six.midValue()) > 0);
        assertTrue(six.midValue().compareTo(twelve.midValue()) > 0);
        assertTrue(twelve.lowValue().compareTo(twelve.highValue()) < 0);
    }

    @Test
    void holdingCostsBringTheBreakEvenForward() {
        // month 10: mid ~26,345 - 1,500 = 24,845 (still above 24,500); month 11: ~26,139 - 1,650 = 24,489
        DepreciationForecast f = calculator.forecast(cx5(150));
        assertEquals(11, f.breakEvenMonth().orElseThrow());
        assertEquals(Money.cad(1_800), f.pointAt(12).orElseThrow().cumulativeHoldingCost());
    }

    @Test
    void noBreakEvenWhenWaitingStaysWorthIt() {
        assertFalse(calculator.forecast(cx5(0)).breakEvenMonth().isPresent());
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.forecast(
                new ForecastInput(Money.ZERO, 2022, Segment.SUV, TODAY, null, Money.cad(1))));
        assertThrows(IllegalArgumentException.class, () -> calculator.forecast(cx5(-10)));
    }

    @Test
    void unknownSegmentUsesDefaultRatesWithWarning() {
        DepreciationForecast f = calculator.forecast(
                new ForecastInput(Money.cad(20_000), 2020, null, TODAY, null, Money.cad(15_000)));
        assertEquals(Segment.ECONOMY, f.segment());
        assertTrue(f.warnings().get(0).contains("segment is unknown"));
    }

    @Test
    void vehicleOlderThanTableUsesOldestBracketWithWarning() {
        DepreciationForecast f = calculator.forecast(
                new ForecastInput(Money.cad(5_000), 2000, Segment.ECONOMY, TODAY, null, Money.cad(4_000)));
        assertTrue(f.warnings().get(0).contains("older than the rate table covers"));
        assertEquals(new RateBand(0.04, 0.08), DepreciationRateTable.builtIn().bandFor(Segment.ECONOMY, 26));
    }

    @Test
    void shippedConfigFileMatchesBuiltInDefaults() {
        DepreciationRateTable file = DepreciationRateTable.loadOrDefault(DepreciationRateTable.DEFAULT_FILE);
        DepreciationRateTable builtIn = DepreciationRateTable.builtIn();
        assertFalse(file.loadWarning().isPresent(), () -> file.loadWarning().orElse(""));
        for (Segment s : Segment.values()) {
            for (int age = 0; age <= 16; age++) {
                assertEquals(builtIn.bandFor(s, age), file.bandFor(s, age), s + " age " + age);
            }
        }
    }

    @Test
    void missingOrCorruptTableFallsBackToDefaultsWithWarning(@TempDir Path dir) throws IOException {
        DepreciationRateTable missing = DepreciationRateTable.loadOrDefault(dir.resolve("none.csv"));
        assertTrue(missing.loadWarning().isPresent());
        Path corrupt = Files.writeString(dir.resolve("bad.csv"), "SUV,0,1,not-a-number,0.2\n");
        DepreciationRateTable bad = DepreciationRateTable.loadOrDefault(corrupt);
        assertTrue(bad.loadWarning().isPresent());
        assertEquals(new RateBand(0.07, 0.11), bad.bandFor(Segment.SUV, 4));

        DepreciationForecast f = new DepreciationCalculator(bad).forecast(cx5(0));
        assertTrue(f.warnings().get(0).contains("built-in default rates"));
    }
}
