package avsa.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A marketplace's posting rules (F08). {@code lastVerified} is null while the
 * limits have not been checked against the platform's own posting form.
 */
public record PlatformProfile(Platform platform, Map<String, Integer> fieldLimits, List<String> requiredFields,
                              String priceFormat, LocalDate lastVerified) {

    public PlatformProfile {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(priceFormat, "priceFormat");
        fieldLimits = Map.copyOf(fieldLimits);
        requiredFields = List.copyOf(requiredFields);
    }

    public OptionalInt limitOf(String field) {
        Integer limit = fieldLimits.get(field);
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }
}
