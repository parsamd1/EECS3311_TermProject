package avsa.infrastructure;

import avsa.domain.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Brings saved session files up to the current schema version (F10). */
public final class SchemaMigrator {

    private final int currentVersion;

    public SchemaMigrator() {
        this(Session.CURRENT_SCHEMA_VERSION);
    }

    SchemaMigrator(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }

    /** Older files can be migrated; files from a newer version of the app cannot. */
    public boolean canMigrate(int fromVersion) {
        return fromVersion >= 0 && fromVersion <= currentVersion;
    }

    /** A file with no version predates versioning and differs only by the missing field. */
    public JsonNode migrate(JsonNode json) {
        ObjectNode migrated = ((ObjectNode) json).deepCopy();
        migrated.put("schemaVersion", currentVersion);
        return migrated;
    }
}
