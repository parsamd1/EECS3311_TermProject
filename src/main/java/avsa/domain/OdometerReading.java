package avsa.domain;

import java.time.LocalDate;

/** An odometer value recorded by a third party. The date may be null. */
public record OdometerReading(LocalDate date, int odometerKm, String reportedBy) {
}
