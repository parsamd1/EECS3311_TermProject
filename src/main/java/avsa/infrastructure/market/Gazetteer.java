package avsa.infrastructure.market;

import avsa.domain.Location;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Coordinates of Canadian cities, from a file bundled with the application. */
public final class Gazetteer {

    private static final String RESOURCE = "/gazetteer.csv";

    private final Map<String, Location> places = new HashMap<>();

    public Gazetteer() {
        try (InputStream in = Gazetteer.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    String[] f = line.split(",");
                    places.put(key(f[0], f[1]), new Location(f[0], f[1], Double.parseDouble(f[2]),
                            Double.parseDouble(f[3])));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Location> resolve(String city, String province) {
        return Optional.ofNullable(places.get(key(city, province)));
    }

    private static String key(String city, String province) {
        return city.strip().toLowerCase(Locale.ROOT) + "|" + province.strip().toLowerCase(Locale.ROOT);
    }
}
