package avsa.domain;

import java.time.LocalDate;

/** A reported accident. Date and severity may be null when the source does not give them. */
public record AccidentRecord(LocalDate date, Severity severity, String description) {
}
