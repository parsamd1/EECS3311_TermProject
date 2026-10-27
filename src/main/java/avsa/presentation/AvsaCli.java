package avsa.presentation;

import avsa.infrastructure.AppConfig;
import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * Command-line front end ({@code avsa}). Feature commands are added here as the
 * features are implemented; every command goes through the same application
 * facade as the GUI.
 */
public final class AvsaCli {

    static final String VERSION = "0.1.0";

    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 2;

    private final PrintStream out;
    private final PrintStream err;
    private final Supplier<AppConfig> config;

    AvsaCli(PrintStream out, PrintStream err, Supplier<AppConfig> config) {
        this.out = out;
        this.err = err;
        this.config = config;
    }

    public static void main(String[] args) {
        int code = new AvsaCli(System.out, System.err, AppConfig::load).run(args);
        if (code != EXIT_OK) {
            System.exit(code);
        }
    }

    int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return EXIT_OK;
        }
        return switch (args[0]) {
            case "--help", "-h", "help" -> {
                printUsage();
                yield EXIT_OK;
            }
            case "--version" -> {
                out.println("avsa " + VERSION);
                yield EXIT_OK;
            }
            case "config" -> showConfig();
            default -> {
                err.println("Unknown command: " + args[0]);
                err.println("Run 'avsa --help' to see the available commands.");
                yield EXIT_USAGE;
            }
        };
    }

    private int showConfig() {
        AppConfig cfg = config.get();
        out.println("LLM model: " + cfg.llmModelId());
        out.println("API key:   " + cfg.maskedApiKey());
        if (cfg.llmApiKey().isEmpty()) {
            out.println();
            out.println("No API key configured. Copy .env.example to .env and set "
                    + AppConfig.API_KEY + " there.");
        }
        return EXIT_OK;
    }

    private void printUsage() {
        out.println("AVSA - AI Vehicle Sales Assistant " + VERSION);
        out.println();
        out.println("Usage: avsa <command> [options]");
        out.println();
        out.println("Commands:");
        out.println("  config       Show the LLM model and whether an API key is configured");
        out.println();
        out.println("Options:");
        out.println("  --help       Show this help");
        out.println("  --version    Show the version");
    }
}
