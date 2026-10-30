package avsa.services;

import avsa.domain.ComparableListing;
import avsa.domain.Money;
import avsa.domain.PricingParameters;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the pricing engine needs. {@code manualBaseline} is the seller's own
 * estimate, used only when there are too few comparables. {@code history} may be
 * null: neutral assumptions are then used and confidence is lowered.
 */
public record PricingInput(VehicleProfile profile, List<ComparableListing> comparables,
                           PricingParameters parameters, Money manualBaseline, VehicleHistory history) {

    public PricingInput {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(parameters, "parameters");
        comparables = List.copyOf(comparables);
    }

    public PricingInput(VehicleProfile profile, List<ComparableListing> comparables, PricingParameters parameters) {
        this(profile, comparables, parameters, null, null);
    }

    public PricingInput(VehicleProfile profile, List<ComparableListing> comparables, PricingParameters parameters,
                        Money manualBaseline) {
        this(profile, comparables, parameters, manualBaseline, null);
    }

    public PricingInput withHistory(VehicleHistory vehicleHistory) {
        return new PricingInput(profile, comparables, parameters, manualBaseline, vehicleHistory);
    }

    public Optional<Money> manualBaselineValue() {
        return Optional.ofNullable(manualBaseline);
    }
}
