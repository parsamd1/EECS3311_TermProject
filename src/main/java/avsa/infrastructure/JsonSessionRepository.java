package avsa.infrastructure;

import avsa.domain.Session;
import avsa.domain.SessionSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores each session as one JSON file. Writes go to a temporary file and are then
 * moved into place, so a crash cannot leave a half-written session. Corrupt files
 * are moved to a quarantine folder so the rest of the data stays usable.
 */
public final class JsonSessionRepository implements SessionRepository {

    private final Path dataDir;
    private final Path quarantineDir;
    private final JsonMapper mapper;
    private final SchemaMigrator migrator;
    private final Map<UUID, Session> pending = new LinkedHashMap<>();
    private final List<String> quarantined = new ArrayList<>();

    public JsonSessionRepository(Path dataDir, JsonMapper mapper, SchemaMigrator migrator) {
        this.dataDir = dataDir;
        this.quarantineDir = dataDir.resolve("quarantine");
        this.mapper = mapper;
        this.migrator = migrator;
    }

    @Override
    public void save(Session session) {
        pending.put(session.id(), session);
        pending.values().removeIf(this::tryWrite);
    }

    private boolean tryWrite(Session session) {
        Path target = fileFor(session.id());
        try {
            byte[] json = mapper.toBytes(session);
            Files.createDirectories(dataDir);
            Path temp = Files.createTempFile(dataDir, session.id().toString(), ".tmp");
            Files.write(temp, json);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Session load(UUID id) {
        Session unsaved = pending.get(id);
        if (unsaved != null) {
            return unsaved;
        }
        return loadFile(fileFor(id));
    }

    private Session loadFile(Path file) {
        JsonNode tree;
        try {
            tree = mapper.readTree(file);
        } catch (NoSuchFileException e) {
            throw new SessionLoadException("Saved session not found: " + file.getFileName(), e);
        } catch (JsonProcessingException e) {
            throw corrupt(file, e);
        } catch (IOException e) {
            throw new SessionLoadException("Saved session could not be read: " + file.getFileName(), e);
        }
        if (tree == null || !tree.isObject()) {
            throw corrupt(file, null);
        }
        int version = tree.path("schemaVersion").asInt(0);
        boolean migratable = migrator.canMigrate(version);
        try {
            Session session = mapper.fromTree(migratable ? migrator.migrate(tree) : tree, Session.class);
            if (!migratable) {
                session.markReadOnly();
            }
            return session;
        } catch (JsonProcessingException | RuntimeException e) {
            throw corrupt(file, e);
        }
    }

    private SessionLoadException corrupt(Path file, Exception cause) {
        try {
            Files.createDirectories(quarantineDir);
            Files.move(file, quarantineDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            quarantined.add(file.getFileName().toString());
        } catch (IOException moveFailure) {
            // Leave the file in place: nothing is lost, and the load still reports the problem.
        }
        return new SessionLoadException("Saved session is corrupt and was quarantined: " + file.getFileName(), cause);
    }

    @Override
    public List<SessionSummary> list() {
        List<SessionSummary> summaries = new ArrayList<>();
        if (Files.isDirectory(dataDir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dataDir, "*.json")) {
                for (Path file : files) {
                    try {
                        Session s = loadFile(file);
                        summaries.add(new SessionSummary(s.id(), s.name(), s.lastModified()));
                    } catch (SessionLoadException e) {
                        // Reported through quarantined(); the other sessions remain usable.
                    }
                }
            } catch (IOException e) {
                throw new SessionLoadException("The sessions folder could not be read: " + dataDir, e);
            }
        }
        summaries.sort(Comparator.comparing(SessionSummary::lastModified).reversed());
        return summaries;
    }

    @Override
    public void delete(UUID id) {
        pending.remove(id);
        try {
            Files.deleteIfExists(fileFor(id));
        } catch (IOException e) {
            throw new SessionLoadException("Saved session could not be deleted: " + id, e);
        }
    }

    @Override
    public int pendingWrites() {
        return pending.size();
    }

    @Override
    public List<String> quarantined() {
        return List.copyOf(quarantined);
    }

    private Path fileFor(UUID id) {
        return dataDir.resolve(id + ".json");
    }
}
