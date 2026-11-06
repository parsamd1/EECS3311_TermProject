package avsa.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import avsa.domain.BuyerType;
import avsa.domain.BodyStyle;
import avsa.domain.Money;
import avsa.domain.PriceRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiTest {

    @Test
    void moneyIsShownWithThousandsSeparatorsAndALeadingMinus() {
        assertEquals("$28,900", Ui.cad(Money.cad(28_900)));
        assertEquals("-$290", Ui.cad(Money.cad(-290)));
        assertEquals("$22,900 - $24,100", Ui.cad(new PriceRange(Money.cad(22_900), Money.cad(24_100))));
    }

    @Test
    void typedAmountsAcceptDollarSignsAndCommas() {
        assertEquals(Money.cad(24_500), Ui.parseMoney("$24,500", "Offer"));
        assertNull(Ui.parseMoney("  ", "Offer"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Ui.parseMoney("abc", "Offer"));
        assertEquals("Offer must be an amount in dollars, such as 24,500.", e.getMessage());
        assertEquals(49_000, Ui.integer("49,000", "Odometer"));
    }

    @Test
    void enumNamesReadAsWords() {
        assertEquals("Dealer", Ui.label(BuyerType.DEALER));
        assertEquals("SUV", Ui.label(BodyStyle.SUV));
        assertEquals(List.of("Winter tires", "heated seats"), Ui.list("Winter tires, heated seats,"));
    }
}
