package avsa.infrastructure.history;

import avsa.domain.VehicleHistory;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What the seller supplied: a report file, or values typed into the manual form. */
public final class HistoryInput {

    private final Path file;
    private final VehicleHistory manualEntry;

    private HistoryInput(Path file, VehicleHistory manualEntry) {
        this.file = file;
        this.manualEntry = manualEntry;
    }

    public static HistoryInput fromFile(Path file) {
        return new HistoryInput(Objects.requireNonNull(file), null);
    }

    public static HistoryInput manual(VehicleHistory entered) {
        return new HistoryInput(null, Objects.requireNonNull(entered));
    }

    public Optional<Path> file() { return Optional.ofNullable(file); }
    public Optional<VehicleHistory> manualEntry() { return Optional.ofNullable(manualEntry); }

    boolean hasFileExtension(String... extensions) {
        if (file == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        for (String ext : extensions) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
