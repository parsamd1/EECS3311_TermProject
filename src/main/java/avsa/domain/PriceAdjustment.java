package avsa.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One itemized change to the baseline price. AI-proposed adjustments only
 * count when the seller has accepted them.
 */
public record PriceAdjustment(UUID id, String label, Money amount, AdjustmentKind kind, boolean accepted) {

    public PriceAdjustment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(kind, "kind");
    }

    public static PriceAdjustment computed(String label, Money amount, AdjustmentKind kind) {
        return new PriceAdjustment(UUID.randomUUID(), label, amount, kind, true);
    }

    public boolean applies() {
        return kind != AdjustmentKind.AI_PROPOSED || accepted;
    }
}
