package avsa.infrastructure;

import avsa.domain.Session;
import avsa.domain.SessionSummary;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Local persistence of sessions (F10). */
public interface SessionRepository {

    /** Saves the session; if the disk fails the data is kept in memory and retried on the next save. */
    void save(Session session);

    /** @throws SessionLoadException the session is missing or corrupt */
    Session load(UUID id);

    /** Readable sessions only; corrupt files are quarantined and reported by {@link #quarantined()}. */
    List<SessionSummary> list();

    void delete(UUID id);

    /**
     * Copies every saved session file into {@code target}, which is created if needed.
     *
     * @return the number of sessions copied
     */
    int backup(Path target);

    /** Number of sessions waiting to be written after a disk error. */
    int pendingWrites();

    /** File names moved to quarantine during this run. */
    List<String> quarantined();
}
