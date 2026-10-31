package avsa.domain;

import java.util.Objects;

/** A problem found in a listing's text, with a one-line reason. */
public record ListingFlag(FlagCode code, String reason) {

    public ListingFlag {
        Objects.requireNonNull(code, "code");
        reason = reason == null ? "" : reason.strip();
    }
}
