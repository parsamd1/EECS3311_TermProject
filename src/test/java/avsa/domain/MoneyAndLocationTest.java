package avsa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyAndLocationTest {

    @Test
    void moneyArithmeticKeepsCents() {
        Money a = Money.cad(100.005);
        assertEquals(new BigDecimal("100.01"), a.amount());
        assertEquals(Money.cad(150), Money.cad(100).plus(Money.cad(50)));
        assertEquals(Money.cad(50), Money.cad(100).minus(Money.cad(50)));
        assertEquals(Money.cad(86), Money.cad(100).times(0.86));
        assertTrue(Money.cad(1).isPositive());
    }

    @Test
    void roundToNearestStep() {
        assertEquals(Money.cad(24_100), Money.cad(24_080).roundTo(100));
        assertEquals(Money.cad(24_000), Money.cad(24_049.99).roundTo(100));
    }

    @Test
    void currenciesCannotBeMixed() {
        Money usd = new Money(BigDecimal.TEN, "USD");
        assertThrows(IllegalArgumentException.class, () -> Money.cad(10).plus(usd));
    }

    @Test
    void distanceTorontoToMississaugaIsAbout22Km() {
        Location mississauga = new Location("Mississauga", "ON", 43.5890, -79.6441);
        double d = TestVehicles.TORONTO.distanceKmTo(mississauga);
        assertTrue(d > 20 && d < 25, "was " + d);
        assertEquals(0.0, TestVehicles.TORONTO.distanceKmTo(TestVehicles.TORONTO), 1e-9);
    }
}
