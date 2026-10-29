package avsa.services;

import avsa.domain.Segment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Annual depreciation rate bands by segment and age bracket (F06), read from an
 * editable CSV file. If the file is missing or corrupt the built-in defaults are
 * used and {@link #loadWarning()} says why.
 */
public final class DepreciationRateTable {

    public static final Path DEFAULT_FILE = Path.of("config", "depreciation-rates.csv");

    record Bracket(int minAge, int maxAge, RateBand band) {
    }

    private final Map<Segment, List<Bracket>> brackets;
    private final String loadWarning;

    private DepreciationRateTable(Map<Segment, List<Bracket>> brackets, String loadWarning) {
        this.brackets = brackets;
        this.loadWarning = loadWarning;
    }

    /** Built-in defaults; identical to the shipped config/depreciation-rates.csv. */
    public static DepreciationRateTable builtIn() {
        return new DepreciationRateTable(parse(BUILT_IN), null);
    }

    public static DepreciationRateTable loadOrDefault(Path file) {
        try {
            return new DepreciationRateTable(parse(Files.readAllLines(file, StandardCharsets.UTF_8)), null);
        } catch (IOException | RuntimeException e) {
            return new DepreciationRateTable(parse(BUILT_IN),
                    "Depreciation rate table " + file + " could not be read (" + e.getMessage()
                            + "); built-in default rates were used.");
        }
    }

    public Optional<String> loadWarning() {
        return Optional.ofNullable(loadWarning);
    }

    /** The bracket covering {@code ageYears}, or the oldest bracket when the vehicle is older than the table. */
    public RateBand bandFor(Segment segment, int ageYears) {
        List<Bracket> list = brackets.get(segment);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("No depreciation rates for segment " + segment);
        }
        for (Bracket b : list) {
            if (ageYears >= b.minAge() && ageYears <= b.maxAge()) {
                return b.band();
            }
        }
        return list.get(list.size() - 1).band();
    }

    public boolean covers(Segment segment, int ageYears) {
        return brackets.getOrDefault(segment, List.of()).stream()
                .anyMatch(b -> ageYears >= b.minAge() && ageYears <= b.maxAge());
    }

    static Map<Segment, List<Bracket>> parse(List<String> lines) {
        Map<Segment, List<Bracket>> result = new EnumMap<>(Segment.class);
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split(",");
            if (f.length != 5) {
                throw new IllegalArgumentException("Expected 5 columns: " + line);
            }
            Segment segment = Segment.valueOf(f[0].strip().toUpperCase(Locale.ROOT));
            Bracket b = new Bracket(Integer.parseInt(f[1].strip()), Integer.parseInt(f[2].strip()),
                    new RateBand(Double.parseDouble(f[3].strip()), Double.parseDouble(f[4].strip())));
            result.computeIfAbsent(segment, s -> new ArrayList<>()).add(b);
        }
        for (Segment s : Segment.values()) {
            if (!result.containsKey(s)) {
                throw new IllegalArgumentException("Missing rates for segment " + s);
            }
            result.get(s).sort((a, b) -> Integer.compare(a.minAge(), b.minAge()));
        }
        return result;
    }

    private static final List<String> BUILT_IN = List.of(
            "ECONOMY,0,1,0.15,0.20", "ECONOMY,2,3,0.10,0.15", "ECONOMY,4,6,0.08,0.12",
            "ECONOMY,7,10,0.06,0.10", "ECONOMY,11,15,0.04,0.08",
            "SUV,0,1,0.13,0.18", "SUV,2,3,0.09,0.13", "SUV,4,6,0.07,0.11",
            "SUV,7,10,0.06,0.09", "SUV,11,15,0.04,0.07",
            "TRUCK,0,1,0.12,0.16", "TRUCK,2,3,0.08,0.12", "TRUCK,4,6,0.06,0.10",
            "TRUCK,7,10,0.05,0.08", "TRUCK,11,15,0.04,0.07",
            "LUXURY,0,1,0.20,0.25", "LUXURY,2,3,0.13,0.18", "LUXURY,4,6,0.10,0.14",
            "LUXURY,7,10,0.07,0.11", "LUXURY,11,15,0.05,0.09",
            "EV,0,1,0.20,0.28", "EV,2,3,0.14,0.20", "EV,4,6,0.10,0.15",
            "EV,7,10,0.08,0.12", "EV,11,15,0.06,0.10");
}
