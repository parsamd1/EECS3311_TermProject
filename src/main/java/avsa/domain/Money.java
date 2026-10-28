package avsa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** An amount of money in Canadian dollars, kept to the cent. */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    public static final String CAD = "CAD";
    public static final Money ZERO = cad(0);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money cad(long dollars) {
        return new Money(BigDecimal.valueOf(dollars), CAD);
    }

    public static Money cad(double dollars) {
        return new Money(BigDecimal.valueOf(dollars), CAD);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(double factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    /** Rounded to the nearest multiple of {@code step} dollars (e.g. 100). */
    public Money roundTo(long step) {
        BigDecimal s = BigDecimal.valueOf(step);
        return new Money(amount.divide(s, 0, RoundingMode.HALF_UP).multiply(s), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public double toDouble() {
        return amount.doubleValue();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return "$" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
