package avsa.infrastructure;

import avsa.domain.Platform;
import avsa.domain.PlatformProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads each marketplace's posting rules from an editable file, so limits can change without a
 * code change. If the file is missing or corrupt the same built-in values are used.
 */
public final class PlatformProfileLoader {

    public static final Path DEFAULT_FILE = Path.of("config", "platform-profiles.json");

    private final Map<Platform, PlatformProfile> profiles = new EnumMap<>(Platform.class);

    public PlatformProfileLoader() {
        this(DEFAULT_FILE, new JsonMapper());
    }

    public PlatformProfileLoader(Path file, JsonMapper mapper) {
        for (Platform platform : Platform.values()) {
            profiles.put(platform, builtIn(platform));
        }
        try {
            for (PlatformProfile profile : mapper.read(file, PlatformProfile[].class)) {
                profiles.put(profile.platform(), profile);
            }
        } catch (IOException | RuntimeException e) {
            for (Platform platform : Platform.values()) {
                profiles.put(platform, builtIn(platform));
            }
        }
    }

    public PlatformProfile load(Platform platform) {
        return profiles.get(platform);
    }

    static PlatformProfile builtIn(Platform platform) {
        int titleLimit = switch (platform) {
            case AUTOTRADER -> 80;
            case KIJIJI -> 64;
            case FACEBOOK_MARKETPLACE -> 100;
        };
        int bodyLimit = platform == Platform.FACEBOOK_MARKETPLACE ? 5000 : 4000;
        return new PlatformProfile(platform, Map.of("title", titleLimit, "body", bodyLimit),
                List.of("title", "price", "description"), "$#,##0", null);
    }
}
