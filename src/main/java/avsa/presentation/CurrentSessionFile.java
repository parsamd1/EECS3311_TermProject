package avsa.presentation;

import avsa.application.SessionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Remembers which session is open, in {@code <dataDir>/current-session}, so the CLI (one process
 * per command) and the GUI continue with the same vehicle.
 */
final class CurrentSessionFile {

    private final Path pointer;

    CurrentSessionFile(Path dataDir) {
        this.pointer = dataDir.resolve("current-session");
    }

    /** Opens the remembered session; does nothing when it is gone or unreadable. */
    void restore(SessionService sessions) {
        try {
            if (Files.isReadable(pointer)) {
                sessions.open(UUID.fromString(Files.readString(pointer, StandardCharsets.UTF_8).strip()));
            }
        } catch (IOException | RuntimeException e) {
            // The remembered session is gone or unreadable; start without one.
        }
    }

    /** @throws IOException the data folder cannot be written */
    void remember(SessionService sessions) throws IOException {
        Files.createDirectories(pointer.getParent());
        if (sessions.hasCurrent()) {
            Files.writeString(pointer, sessions.current().id().toString(), StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(pointer);
        }
    }
}
