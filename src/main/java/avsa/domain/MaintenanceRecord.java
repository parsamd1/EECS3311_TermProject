package avsa.domain;

import java.time.LocalDate;

public record MaintenanceRecord(LocalDate date, int odometerKm, String description) {
}
