package avsa.presentation;

import java.io.PrintStream;

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

    AvsaCli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int code = new AvsaCli(System.out, System.err).run(args);
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
            default -> {
                err.println("Unknown command: " + args[0]);
                err.println("Run 'avsa --help' to see the available commands.");
                yield EXIT_USAGE;
            }
        };
    }

    private void printUsage() {
        out.println("AVSA - AI Vehicle Sales Assistant " + VERSION);
        out.println();
        out.println("Usage: avsa <command> [options]");
        out.println();
        out.println("Options:");
        out.println("  --help       Show this help");
        out.println("  --version    Show the version");
    }
}
