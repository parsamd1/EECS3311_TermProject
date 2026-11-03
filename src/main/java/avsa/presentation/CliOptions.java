package avsa.presentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Command-line words: positional words, "--name value" options and "--flag" switches. */
final class CliOptions {

    private final List<String> words = new ArrayList<>();
    private final Map<String, String> options = new HashMap<>();
    private final Set<String> flags = new HashSet<>();

    CliOptions(String[] args, Set<String> knownFlags) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String name = arg.substring(2);
                if (knownFlags.contains(name)) {
                    flags.add(name);
                } else if (i + 1 < args.length) {
                    options.put(name, args[++i]);
                } else {
                    throw new IllegalArgumentException("Option --" + name + " needs a value.");
                }
            } else {
                words.add(arg);
            }
        }
    }

    String word(int index) {
        return index < words.size() ? words.get(index) : "";
    }

    Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

    String required(String name) {
        return option(name).orElseThrow(() -> new IllegalArgumentException("Missing option --" + name + "."));
    }

    int intOption(String name, int fallback) {
        return option(name).map(v -> parseInt(name, v)).orElse(fallback);
    }

    static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value.replace(",", "").replace("_", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be a whole number.");
        }
    }

    boolean flag(String name) {
        return flags.contains(name);
    }
}
