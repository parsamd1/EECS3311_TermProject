package avsa.application;

import avsa.domain.MarketSnapshot;
import avsa.domain.Session;
import avsa.domain.SessionSummary;
import avsa.domain.SnapshotComparison;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.SessionRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The session operations used by the Sessions panel and the CLI (F10). */
public final class SessionService {

    private final SessionRepository repository;
    private final Clock clock;
    private Session current;

    public SessionService(SessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @throws IllegalStateException no session is open; features need a profile first (F01) */
    public Session current() {
        if (current == null) {
            throw new IllegalStateException("No vehicle profile yet. Create one first.");
        }
        return current;
    }

    public boolean hasCurrent() {
        return current != null;
    }

    public Session create(VehicleProfile profile) {
        String name = profile.year() + " " + profile.make() + " " + profile.model();
        current = new Session(UUID.randomUUID(), name, profile, clock.instant());
        repository.save(current);
        return current;
    }

    /** A saved session for the same vehicle, so the seller can open it instead of creating a duplicate. */
    public Optional<SessionSummary> findMatching(VehicleProfile profile) {
        return repository.list().stream()
                .filter(s -> repository.load(s.id()).activeProfile().sameVehicleAs(profile))
                .findFirst();
    }

    /** Saves the current session unless it was opened read-only. Does nothing when no session is open. */
    public void saveCurrent() {
        if (current != null && !current.readOnly()) {
            current.touch(clock.instant());
            repository.save(current);
        }
    }

    public List<SessionSummary> list() {
        return repository.list();
    }

    public Session open(UUID id) {
        current = repository.load(id);
        return current;
    }

    public void rename(UUID id, String newName) {
        Session session = repository.load(id);
        session.rename(newName);
        session.touch(clock.instant());
        repository.save(session);
        if (current != null && current.id().equals(id)) {
            current = session;
        }
    }

    public Session duplicate(UUID id) {
        Session source = repository.load(id);
        Session copy = source.copyAs(UUID.randomUUID(), source.name() + " (copy)", clock.instant());
        repository.save(copy);
        return copy;
    }

    /** The caller asks the seller to confirm first (F10). */
    public void delete(UUID id) {
        repository.delete(id);
        if (current != null && current.id().equals(id)) {
            current = null;
        }
    }

    /** Compares two market snapshots of the current session. */
    public SnapshotComparison compare(UUID first, UUID second) {
        return SnapshotComparison.of(snapshot(first), snapshot(second));
    }

    private MarketSnapshot snapshot(UUID id) {
        return current().snapshots().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No market snapshot with id " + id));
    }

    /**
     * Copies all saved sessions into a new dated folder inside {@code folder} (F10).
     *
     * @return the folder the backup was written to
     */
    public Path backup(Path folder) {
        saveCurrent();
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(clock.getZone()).format(clock.instant());
        Path target = folder.resolve("avsa-backup-" + stamp);
        repository.backup(target);
        return target;
    }

    /** True while a disk error is keeping data in memory; the seller should be warned. */
    public boolean hasUnsavedChanges() {
        return repository.pendingWrites() > 0;
    }

    public List<String> quarantinedFiles() {
        return repository.quarantined();
    }
}
