package avsa.presentation;

import avsa.application.AgentController;
import avsa.application.AgentEvent;
import avsa.application.AvsaApplication;
import avsa.application.EventListener;
import avsa.application.SessionService;
import avsa.domain.AccidentRecord;
import avsa.domain.BodyStyle;
import avsa.domain.ChatTurn;
import avsa.domain.CoachingReport;
import avsa.domain.Claim;
import avsa.domain.ClaimKind;
import avsa.domain.BuyerType;
import avsa.domain.ComparableListing;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DepreciationForecast;
import avsa.domain.ForecastPoint;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.ExportedListing;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingFlag;
import avsa.domain.ListingOptions;
import avsa.domain.Location;
import avsa.domain.MarketSnapshot;
import avsa.domain.Persona;
import avsa.domain.Platform;
import avsa.domain.PositioningReport;
import avsa.domain.PriceAdjustment;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Session;
import avsa.domain.SessionSummary;
import avsa.domain.Severity;
import avsa.domain.SimulationSession;
import avsa.domain.SimulationStatus;
import avsa.domain.Speaker;
import avsa.domain.StrategyType;
import avsa.domain.Tone;
import avsa.domain.Transmission;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.AppConfig;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.history.HistoryImportResult;
import avsa.infrastructure.history.HistoryInput;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.MarketSourceType;
import avsa.services.ListingExporter;
import avsa.services.SearchRequest;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Command-line front end ({@code avsa}). Every command goes through the same
 * {@link AgentController} facade as the GUI. The current session is remembered between
 * commands in the data folder.
 */
public final class AvsaCli implements EventListener {

    static final String VERSION = "0.2.0";

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final Set<String> FLAGS = Set.of("json", "accept", "live", "help", "version", "no-features",
            "no-maintenance", "confirm");
    private static final Set<String> COMMANDS = Set.of("profile", "session", "comps", "price", "doctor", "listing",
            "export", "offer", "forecast", "history", "position", "simulate");

    private final PrintStream out;
    private final PrintStream err;
    private final Supplier<AppConfig> config;
    private final Function<AppConfig, AgentController> controllers;
    private final Path dataDir;

    AvsaCli(PrintStream out, PrintStream err, Supplier<AppConfig> config,
            Function<AppConfig, AgentController> controllers, Path dataDir) {
        this.out = out;
        this.err = err;
        this.config = config;
        this.controllers = controllers;
        this.dataDir = dataDir;
    }

    public static void main(String[] args) {
        Path dataDir = dataDir();
        int code = new AvsaCli(System.out, System.err, AppConfig::load,
                cfg -> AvsaApplication.create(cfg, dataDir, Clock.systemDefaultZone(), null), dataDir).run(args);
        if (code != EXIT_OK) {
            System.exit(code);
        }
    }

    /** Session files can contain a VIN, so they are kept outside the project: AVSA_DATA_DIR or ~/.avsa. */
    static Path dataDir() {
        String custom = System.getenv("AVSA_DATA_DIR");
        return custom != null && !custom.isBlank() ? Path.of(custom) : Path.of(System.getProperty("user.home"), ".avsa");
    }

    int run(String[] args) {
        CliOptions options;
        try {
            options = new CliOptions(args, FLAGS);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return EXIT_USAGE;
        }
        String command = options.word(0);
        if (options.flag("version")) {
            out.println("avsa " + VERSION);
            return EXIT_OK;
        }
        if (command.isEmpty() || command.equals("help") || options.flag("help") || command.equals("-h")) {
            printUsage();
            return EXIT_OK;
        }
        if (command.equals("config")) {
            return showConfig();
        }
        if (!COMMANDS.contains(command)) {
            err.println("Unknown command: " + command);
            err.println("Run 'avsa --help' to see the available commands.");
            return EXIT_USAGE;
        }
        AgentController controller = controllers.apply(config.get());
        controller.events().subscribe(AgentEvent.class, this);
        restoreCurrentSession(controller.sessions());
        try {
            int code = switch (command) {
                case "profile" -> profile(controller, options);
                case "session" -> session(controller, options);
                case "comps" -> comps(controller, options);
                case "price" -> price(controller, options);
                case "doctor" -> doctor(controller, options);
                case "listing" -> listing(controller, options);
                case "export" -> export(controller, options);
                case "offer" -> offer(controller, options);
                case "forecast" -> forecast(controller, options);
                case "history" -> history(controller, options);
                case "position" -> position(controller);
                case "simulate" -> simulate(controller, options);
                default -> {
                    err.println("Unknown command: " + command);
                    err.println("Run 'avsa --help' to see the available commands.");
                    yield EXIT_USAGE;
                }
            };
            rememberCurrentSession(controller.sessions());
            if (options.flag("json")) {
                out.println(controller.trace().toJson(new JsonMapper()));
            }
            return code;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return EXIT_USAGE;
        } catch (IllegalStateException e) {
            err.println(e.getMessage());
            return EXIT_ERROR;
        }
    }

    /** Events confirm that each change was saved. */
    @Override
    public void onEvent(AgentEvent event) {
        out.println("(saved: " + event.getClass().getSimpleName() + ")");
    }

    private int profile(AgentController controller, CliOptions o) {
        if (o.word(1).equals("show")) {
            VehicleProfile p = controller.sessions().current().activeProfile();
            out.println(describe(p));
            p.condition().defects().forEach(d -> out.println("  defect: " + d.severity().name().toLowerCase()
                    + " " + d.type().name().toLowerCase() + " on the " + d.bodyLocation()));
            return EXIT_OK;
        }
        if (!o.word(1).equals("set")) {
            throw new IllegalArgumentException("Use 'avsa profile set ...' or 'avsa profile show'.");
        }
        String make = o.required("make");
        String model = o.required("model");
        int year = CliOptions.parseInt("year", o.required("year"));
        String city = o.required("city");
        String province = o.required("province");
        Location location = new Gazetteer().resolve(city, province).orElseThrow(() ->
                new IllegalArgumentException("Unknown city: " + city + ", " + province + "."));
        List<Defect> defects = new ArrayList<>();
        o.option("defect").ifPresent(spec -> defects.add(parseDefect(spec)));
        VehicleProfile profile = VehicleProfile.builder()
                .make(make).model(model).year(year).trim(o.option("trim").orElse(""))
                .bodyStyle(parseEnum(BodyStyle.class, "body", o.required("body")))
                .odometerKm(CliOptions.parseInt("km", o.required("km")))
                .transmission(parseEnum(Transmission.class, "transmission", o.option("transmission").orElse("automatic")))
                .fuelType(parseEnum(FuelType.class, "fuel", o.option("fuel").orElse("gasoline")))
                .location(location).features(o.option("features").map(f -> List.of(f.split("\\s*,\\s*"))).orElse(List.of()))
                .condition(new ConditionReport(null, defects))
                .build();
        controller.saveProfile(profile);
        out.println("Profile saved: " + describe(profile));
        out.println("Session: " + controller.sessions().current().id());
        return EXIT_OK;
    }

    /** "scratch:rear door:minor" */
    private static Defect parseDefect(String spec) {
        String[] parts = spec.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("--defect must look like scratch:rear door:minor");
        }
        return new Defect(parseEnum(DefectType.class, "defect", parts[0]), parts[1],
                parseEnum(Severity.class, "defect severity", parts[2]), "", null);
    }

    private int session(AgentController controller, CliOptions o) {
        SessionService sessions = controller.sessions();
        switch (o.word(1)) {
            case "list" -> {
                List<SessionSummary> all = sessions.list();
                if (all.isEmpty()) {
                    out.println("No saved sessions.");
                }
                for (SessionSummary s : all) {
                    String marker = sessions.hasCurrent() && sessions.current().id().equals(s.id()) ? "* " : "  ";
                    out.println(marker + s.id() + "  " + s.name() + "  (" + s.lastModified() + ")");
                }
            }
            case "load" -> {
                Session s = sessions.open(UUID.fromString(o.word(2)));
                out.println("Opened " + s.name() + (s.readOnly() ? " (read-only: saved by a newer version)" : ""));
            }
            case "delete" -> {
                sessions.delete(UUID.fromString(o.word(2)));
                out.println("Deleted.");
            }
            default -> throw new IllegalArgumentException("Use 'avsa session list|load <id>|delete <id>'.");
        }
        return EXIT_OK;
    }

    private int comps(AgentController controller, CliOptions o) {
        if (!o.word(1).equals("search")) {
            throw new IllegalArgumentException("Use 'avsa comps search [--source seeded|live|csv] [--radius km]'.");
        }
        MarketSourceType source = parseEnum(MarketSourceType.class, "source", o.option("source").orElse("seeded"));
        if (source == MarketSourceType.LIVE) {
            out.println(controller.liveSearchConfigured()
                    ? "Searching AutoTrader.ca, Kijiji and CarGurus. This usually takes one to three minutes..."
                    : "Live search needs an API key in .env; the seeded dataset will be used.");
        }
        SearchRequest request = new SearchRequest(o.intOption("radius", SearchRequest.DEFAULT_RADIUS_KM),
                o.intOption("max", SearchRequest.DEFAULT_MAX_RESULTS), SearchRequest.DEFAULT_YEAR_WINDOW, true, null);
        MarketSnapshot s = controller.findComparables(request, source, o.option("csv").map(Path::of).orElse(null));
        printSnapshot(s);
        printNotes(controller);
        return EXIT_OK;
    }

    private void printSnapshot(MarketSnapshot s) {
        out.println("Source: " + s.sourceName() + (s.fromCache() ? " (saved result from " + s.retrievedAt() + ")" : "")
                + "   radius " + s.radiusKm() + " km" + (s.lowConfidence() ? "   LOW CONFIDENCE" : ""));
        out.println(String.format(Locale.ROOT, "%-3s %-6s %-5s %-14s %10s %10s %8s  %-11s %s", "#", "score", "year",
                "trim", "odometer", "price", "distance", "source", "flags"));
        int rank = 1;
        Set<String> links = new LinkedHashSet<>();
        for (ComparableListing l : s.rankedListings()) {
            out.println(String.format(Locale.ROOT, "%-3d %-6.2f %-5d %-14s %,9d km %10s %5.0f km%s  %-11s %s", rank++,
                    l.score(), l.year(), shorten(l.trim(), 14), l.odometerKm(), l.askingPrice(), l.distanceKm(),
                    l.locationApproximate() ? "~" : " ", l.source(), flags(l)));
            if (!l.url().isBlank()) {
                links.add(l.url());
            }
        }
        out.println(String.format(Locale.ROOT, "%d listings: min %s, median %s, max %s", s.stats().count(),
                s.stats().min(), s.stats().median(), s.stats().max()));
        if (!links.isEmpty()) {
            out.println("Sources:");
            links.forEach(link -> out.println("  " + link));
        }
        s.warnings().forEach(w -> out.println("Warning: " + w));
    }

    private static String flags(ComparableListing l) {
        if (!l.reviewed()) {
            return "unreviewed";
        }
        return l.flags().isEmpty() ? "" : String.join(", ", l.flags().stream().map(ListingFlag::code)
                .map(Enum::name).toList());
    }

    private int price(AgentController controller, CliOptions o) {
        StrategyType strategy = parseEnum(StrategyType.class, "strategy", o.option("strategy").orElse("max-profit"));
        PricingParameters params = PricingParameters.defaults();
        if (o.option("margin").isPresent()) {
            params = params.withNegotiationMargin(Double.parseDouble(o.required("margin")));
        }
        if (o.option("spread").isPresent()) {
            params = params.withDealerSpread(Double.parseDouble(o.required("spread")));
        }
        Money baseline = o.option("baseline").map(b -> Money.cad(CliOptions.parseInt("baseline", b))).orElse(null);
        PriceRecommendation rec = controller.recommendPrice(strategy, params, baseline);
        out.println("Strategy:   " + rec.strategy().name().toLowerCase().replace('_', '-'));
        out.println("Retail:     " + rec.retail());
        out.println("Wholesale:  " + rec.wholesale());
        out.println("Asking:     " + rec.askingPrice());
        out.println("Walk away:  " + rec.walkAwayFloor());
        out.println("Confidence: " + rec.confidence().name().toLowerCase());
        rec.adjustments().forEach(a -> out.println("  adjustment: " + a.label() + " " + a.amount()));
        rec.warnings().forEach(w -> out.println("Warning: " + w));
        rec.explanation().ifPresent(e -> out.println((rec.templateExplanation() ? "Explanation (template): "
                : "Explanation (AI): ") + e));
        for (int i = 0; i < rec.proposals().size(); i++) {
            PriceAdjustment p = rec.proposals().get(i);
            out.println("  AI proposal " + (i + 1) + ": " + p.label() + " " + p.amount() + " (not applied)");
        }
        printNotes(controller);
        List<UUID> acceptedAi = new ArrayList<>();
        for (String index : o.option("accept-ai").map(v -> v.split(",")).orElse(new String[0])) {
            int n = CliOptions.parseInt("accept-ai", index.strip());
            if (n < 1 || n > rec.proposals().size()) {
                throw new IllegalArgumentException("There is no AI proposal " + n + ".");
            }
            acceptedAi.add(rec.proposals().get(n - 1).id());
        }
        if (o.flag("accept") || !acceptedAi.isEmpty()) {
            PriceRecommendation active = controller.acceptPrice(rec, acceptedAi);
            out.println("Accepted as the active price" + (acceptedAi.isEmpty() ? "." : " with " + acceptedAi.size()
                    + " AI adjustment(s): retail " + active.retail() + ", asking " + active.askingPrice() + "."));
        }
        return EXIT_OK;
    }

    private int listing(AgentController controller, CliOptions o) {
        GeneratedListing listing = switch (o.word(1)) {
            case "generate" -> controller.generateListing(new ListingOptions(
                    parseEnum(Tone.class, "tone", o.option("tone").orElse("professional")), !o.flag("no-features"),
                    !o.flag("no-maintenance"), o.option("keywords").map(k -> List.of(k.split("\\s*,\\s*")))
                    .orElse(List.of())));
            case "show" -> controller.sessions().current().latestListing().orElseThrow(() ->
                    new IllegalStateException("No listing yet. Run 'avsa listing generate'."));
            case "override" -> controller.overrideListingValidation();
            default -> throw new IllegalArgumentException("Use 'avsa listing generate|show|override'.");
        };
        printListing(listing);
        printNotes(controller);
        return EXIT_OK;
    }

    private void printListing(GeneratedListing l) {
        out.println("Written by: " + (l.templateFallback() ? "template (AI unavailable)" : "AI")
                + (l.editedByUser() ? ", edited by the seller" : ""));
        out.println("Title:       " + l.title());
        out.println("Price:       " + l.price());
        out.println("Description: " + l.description());
        l.highlights().forEach(h -> out.println("  + " + h));
        l.maintenanceDetails().forEach(m -> out.println("  service: " + m));
        out.println("Condition:   " + l.conditionDisclosure());
        if (!l.seoKeywords().isEmpty()) {
            out.println("Keywords:    " + String.join(", ", l.seoKeywords()));
        }
        l.report().ifPresent(r -> {
            if (r.isValid()) {
                out.println("Validation:  passed (defects disclosed, no banned claims, facts and price match)");
            } else {
                out.println("Validation:  FAILED");
                r.violations().forEach(v -> out.println("  x " + v.message()));
            }
        });
        out.println("Exportable:  " + (l.isExportable() ? "yes" + (l.validationOverridden() ? " (warnings overridden)" : "")
                : "no - fix the problems or run 'avsa listing override'"));
    }

    private int export(AgentController controller, CliOptions o) {
        Platform platform = parseEnum(Platform.class, "platform", o.required("platform"));
        ExportedListing e = controller.exportListing(platform, o.option("out").map(Path::of).orElse(null));
        out.println("---- " + platform.displayName() + " ----");
        out.println(e.text());
        out.println("----");
        e.fields().forEach((name, value) -> e.profile().limitOf(name.equals(ListingExporter.TITLE) ? name : "")
                .ifPresent(limit -> out.println(name + ": " + value.length() + " / " + limit + " characters")));
        if (!e.trimmedSections().isEmpty()) {
            out.println("Removed to fit: " + String.join(", ", e.trimmedSections()));
        }
        e.warnings().forEach(w -> out.println("Warning: " + w));
        e.savedTo().ifPresent(p -> out.println("Saved to " + p));
        printNotes(controller);
        return EXIT_OK;
    }

    private int offer(AgentController controller, CliOptions o) {
        if (o.word(1).equals("list")) {
            List<OfferEvaluation> log = controller.sessions().current().offerLog();
            if (log.isEmpty()) {
                out.println("No offers yet.");
            }
            log.forEach(e -> out.println(e.offer().receivedAt() + "  " + e.offer().amount() + " from "
                    + e.offer().buyerType().name().toLowerCase() + ": " + e.verdict().name().toLowerCase().replace('_', ' ')));
            return EXIT_OK;
        }
        if (!o.word(1).equals("add")) {
            throw new IllegalArgumentException("Use 'avsa offer add --amount N --buyer dealer [--notes \"...\"]' or "
                    + "'avsa offer list'.");
        }
        Offer offer = new Offer(Money.cad(CliOptions.parseInt("amount", o.required("amount"))),
                parseEnum(BuyerType.class, "buyer", o.option("buyer").orElse("dealer")), o.option("notes").orElse(""),
                Instant.now());
        OfferEvaluation e = controller.evaluateOffer(offer);
        out.println("Verdict:     " + e.verdict().name().toLowerCase().replace('_', ' ')
                + String.format(Locale.ROOT, " (%.0f%% of the way through the wholesale range)",
                Math.max(0, Math.min(1, e.positionInWholesale())) * 100));
        out.println("Gap to the top of wholesale: " + e.gapToWholesaleTop());
        e.counterAmount().ifPresent(c -> out.println("Counter at:  " + c));
        if (e.belowWalkAwayFloor()) {
            out.println("This offer is below your walk-away floor.");
        }
        e.netIfWaiting().ifPresent(n -> out.println("Waiting for a private buyer is expected to net about " + n
                + " after depreciation and holding costs."));
        e.warnings().forEach(w -> out.println("Warning: " + w));
        e.script().ifPresent(s -> out.println("Script" + (e.templateScript() ? " (template)" : " (AI)") + ":\n" + s));
        printNotes(controller);
        return EXIT_OK;
    }

    private int position(AgentController controller) {
        PositioningReport r = controller.analyzePositioning();
        out.println("Metrics (percentile = share of comparables below yours):");
        r.metrics().forEach(m -> out.println(String.format(Locale.ROOT, "  %-9s %8s   %5.1f%%   (%s is better)",
                m.name(), m.name().equals("year") ? String.valueOf((int) m.value())
                        : String.format(Locale.ROOT, "%,.0f", m.value()), m.percentile(),
                m.betterWhenLower() ? "lower" : "higher")));
        Map<ClaimKind, String> headings = Map.of(ClaimKind.ADVANTAGE, "Advantages", ClaimKind.WEAKNESS, "Weaknesses",
                ClaimKind.REMEDY, "How to address them", ClaimKind.TALKING_POINT, "Talking points");
        for (ClaimKind kind : ClaimKind.values()) {
            List<Claim> claims = r.claims(kind);
            if (!claims.isEmpty()) {
                out.println(headings.get(kind) + ":");
                claims.forEach(c -> out.println("  - " + c.text()));
            }
        }
        if (!r.priceJustification().isBlank()) {
            out.println("Price justification: " + r.priceJustification());
        }
        List<ComparableListing> top = controller.sessions().current().latestSnapshot().map(MarketSnapshot::rankedListings)
                .orElse(List.of()).stream().filter(c -> !c.excluded()).limit(5).toList();
        if (!r.metricsOnly() && !top.isEmpty()) {
            out.println("Comparables cited:");
            for (int i = 0; i < top.size(); i++) {
                ComparableListing c = top.get(i);
                out.println(String.format(Locale.ROOT, "  C%d = %d %s, %,d km, %s (%s)", i + 1, c.year(), c.trim(),
                        c.odometerKm(), c.askingPrice(), c.source()));
            }
        }
        r.notices().forEach(n -> out.println("Note: " + n));
        return EXIT_OK;
    }

    private int simulate(AgentController controller, CliOptions o) {
        switch (o.word(1)) {
            case "start" -> {
                SimulationSession s = controller.startSimulation(parseEnum(Persona.class, "persona",
                        o.option("persona").orElse("serious")));
                out.println("Practising with a " + s.persona().name().toLowerCase().replace('_', '-') + " buyer. Asking "
                        + s.askingPrice() + ", your floor " + s.floor() + ". Reply with: avsa simulate say \"...\"");
                printTurn(s.transcript().get(0));
            }
            case "say" -> {
                String text = o.word(2);
                if (text.isBlank()) {
                    throw new IllegalArgumentException("Put your message in quotes: avsa simulate say \"...\"");
                }
                ChatTurn buyer = controller.sendSellerMessage(text);
                printTurn(buyer);
                SimulationSession s = controller.sessions().current().latestSimulation().orElseThrow();
                if (s.status() != SimulationStatus.ACTIVE) {
                    out.println("The negotiation is over (" + s.status().name().toLowerCase().replace('_', ' ')
                            + "). Run 'avsa simulate end' for coaching.");
                }
            }
            case "end" -> {
                CoachingReport r = controller.endSimulation();
                SimulationSession s = controller.sessions().current().latestSimulation().orElseThrow();
                out.println("Coaching" + (r.aiWritten() ? "" : " (template; AI unavailable)") + ":");
                out.println("  Disclosure: " + r.disclosureFeedback());
                out.println("  Your floor: " + r.floorFeedback());
                r.redFlags().forEach(f -> out.println("  Warning sign: " + f));
                s.listingGaps().forEach(g -> out.println("  The listing did not answer: " + g));
            }
            case "show" -> controller.sessions().current().latestSimulation().orElseThrow(() ->
                    new IllegalStateException("No practice negotiation yet.")).transcript().forEach(this::printTurn);
            default -> throw new IllegalArgumentException(
                    "Use 'avsa simulate start --persona serious|low-baller|dealer|suspicious', 'say \"...\"', 'end' or 'show'.");
        }
        printNotes(controller);
        return EXIT_OK;
    }

    private void printTurn(ChatTurn t) {
        out.println((t.speaker() == Speaker.BUYER ? "Buyer: " : "You:   ") + t.message()
                + t.offer().map(o -> "   [offer " + o + "]").orElse(""));
    }

    private int forecast(AgentController controller, CliOptions o) {
        DepreciationForecast f = controller.forecast(Money.cad(o.intOption("holding", 0)));
        out.println("Starting from " + f.referencePrice() + " (" + f.segment().name().toLowerCase() + " rates), holding "
                + "cost " + f.monthlyHoldingCost() + " a month:");
        for (ForecastPoint p : f.points()) {
            out.println(String.format(Locale.ROOT, "  %2d months: worth %s to %s, %.1f%% lost, net %s", p.months(),
                    p.lowValue(), p.highValue(), p.lossPercent(), p.netIfSoldThen()));
        }
        out.println(f.breakEvenMonth().map(m -> "From month " + m + ", waiting nets less than selling to a dealer today ("
                + f.wholesaleToday() + ").").orElse("Within 12 months, waiting still nets more than a dealer pays today."));
        f.warnings().forEach(w -> out.println("Warning: " + w));
        return EXIT_OK;
    }

    private int history(AgentController controller, CliOptions o) {
        switch (o.word(1)) {
            case "import" -> {
                HistoryImportResult result = controller.importHistory(HistoryInput.fromFile(Path.of(o.required("file"))));
                if (result.needsManualEntry()) {
                    out.println("The report could not be read: " + result.reason().orElse("") + " Use 'avsa history set'.");
                    return EXIT_ERROR;
                }
                VehicleHistory h = result.history();
                printHistory(h);
                result.report().warnings().forEach(w -> out.println((w.blocking() ? "Blocking: " : "Warning: ")
                        + w.message()));
                if (!o.flag("confirm")) {
                    out.println("Check the values, then run the same command with --confirm to use them.");
                } else if (result.report().hasBlockingWarning()) {
                    out.println("Not saved: fix the blocking problem first.");
                    return EXIT_ERROR;
                } else {
                    controller.confirmHistory(h);
                    out.println("History saved. Run pricing again to include it.");
                }
            }
            case "set" -> {
                String accidents = o.required("accidents");
                boolean known = !accidents.equalsIgnoreCase("unknown");
                List<AccidentRecord> records = new ArrayList<>();
                for (int i = 0; known && i < CliOptions.parseInt("accidents", accidents); i++) {
                    records.add(new AccidentRecord(null, null, "Entered by the seller"));
                }
                VehicleHistory h = new VehicleHistory(null, o.option("owners").map(v -> CliOptions.parseInt("owners", v))
                        .orElse(null), null, parseEnum(LienStatus.class, "lien", o.option("lien").orElse("unknown")),
                        o.option("provinces").map(p -> List.of(p.split("\\s*,\\s*"))).orElse(List.of()),
                        HistorySourceType.MANUAL, known, records, List.of());
                controller.confirmHistory(h);
                printHistory(h);
                out.println("History saved. Run pricing again to include it.");
            }
            default -> throw new IllegalArgumentException("Use 'avsa history import --file report.pdf [--confirm]' or "
                    + "'avsa history set --owners N --accidents N|unknown [--lien none|outstanding|unknown]'.");
        }
        return EXIT_OK;
    }

    private void printHistory(VehicleHistory h) {
        out.println("Owners:      " + h.ownerCount().map(String::valueOf).orElse("unknown"));
        out.println("Accidents:   " + (h.accidentsKnown() ? String.valueOf(h.accidents().size()) : "unknown"));
        out.println("Lien:        " + h.lienStatus().name().toLowerCase());
        out.println("Provinces:   " + (h.registrationProvinces().isEmpty() ? "unknown"
                : String.join(" -> ", h.registrationProvinces())));
        h.trustBadges().forEach(b -> out.println("  badge: " + b));
        h.fieldsToConfirm().forEach(f -> out.println("  please confirm: " + f));
    }

    /** The pre-demo check: key, model, and optionally one rehearsal live search. */
    private int doctor(AgentController controller, CliOptions o) {
        AppConfig cfg = config.get();
        boolean ok = true;
        out.println("API key:    " + (cfg.llmApiKey().isPresent() ? "configured (" + cfg.maskedApiKey() + ")" : "MISSING"));
        out.println("Model:      " + cfg.llmModelId());
        if (cfg.llmApiKey().isEmpty()) {
            out.println("Fix: copy .env.example to .env and set " + AppConfig.API_KEY + ".");
            return EXIT_ERROR;
        }
        boolean connected = controller.checkAiConnection();
        out.println("Connection: " + (connected ? "OK" : "FAILED"));
        if (!connected) {
            controller.lastNotes().forEach(n -> out.println("  " + n));
            out.println("Check the internet connection, and the key and credit at platform.claude.com.");
            return EXIT_ERROR;
        }
        out.println("Seeded data: " + (Files.isReadable(AvsaApplication.SEEDED_DATASET) ? "OK" : "MISSING ("
                + AvsaApplication.SEEDED_DATASET + "; run avsa from the project folder)"));
        if (o.flag("live")) {
            if (!controller.sessions().hasCurrent()) {
                out.println("Live check: set a profile first (avsa profile set ...).");
                return EXIT_ERROR;
            }
            out.println("Live check: searching (one to three minutes)...");
            MarketSnapshot s = controller.findComparables(SearchRequest.defaults(), MarketSourceType.LIVE, null);
            boolean live = s.sourceName().equals("Live search") && !s.fromCache();
            out.println("Live check: " + (live ? "OK, " + s.rankedListings().size() + " comparables found and saved "
                    + "as the fallback" : "the live search failed; the app would show " + s.sourceName()
                    + (s.fromCache() ? " from " + s.retrievedAt() : "")));
            ok = live && !s.lowConfidence();
            if (live && s.lowConfidence()) {
                out.println("Live check: few comparables this time; running it again usually finds more.");
            }
        }
        return ok ? EXIT_OK : EXIT_ERROR;
    }

    private void printNotes(AgentController controller) {
        controller.lastNotes().forEach(n -> out.println("Note: " + n));
    }

    private int showConfig() {
        AppConfig cfg = config.get();
        out.println("LLM model: " + cfg.llmModelId());
        out.println("API key:   " + cfg.maskedApiKey());
        if (cfg.llmApiKey().isEmpty()) {
            out.println();
            out.println("No API key configured. Copy .env.example to .env and set " + AppConfig.API_KEY + " there.");
        }
        out.println("Data:      " + dataDir);
        return EXIT_OK;
    }

    private void restoreCurrentSession(SessionService sessions) {
        Path pointer = dataDir.resolve("current-session");
        try {
            if (Files.isReadable(pointer)) {
                sessions.open(UUID.fromString(Files.readString(pointer, StandardCharsets.UTF_8).strip()));
            }
        } catch (IOException | RuntimeException e) {
            // The remembered session is gone or unreadable; commands start without one.
        }
    }

    private void rememberCurrentSession(SessionService sessions) {
        Path pointer = dataDir.resolve("current-session");
        try {
            Files.createDirectories(dataDir);
            if (sessions.hasCurrent()) {
                Files.writeString(pointer, sessions.current().id().toString(), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(pointer);
            }
        } catch (IOException e) {
            err.println("Could not remember the current session: " + e.getMessage());
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String option, String value) {
        String letters = value.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().replace("_", "").equals(letters)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + option + ": " + value + ". Use one of: "
                + String.join(", ", java.util.Arrays.stream(type.getEnumConstants())
                .map(c -> c.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList()) + ".");
    }

    private static String describe(VehicleProfile p) {
        return p.year() + " " + p.make() + " " + p.model() + (p.trim().isBlank() ? "" : " " + p.trim()) + ", "
                + String.format(Locale.ROOT, "%,d", p.odometerKm()) + " km, " + p.location().city() + " "
                + p.location().province();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    private void printUsage() {
        out.println("AVSA - AI Vehicle Sales Assistant " + VERSION);
        out.println();
        out.println("Usage: avsa <command> [options]");
        out.println();
        out.println("Commands:");
        out.println("  config                   Show the model, whether an API key is set, and the data folder");
        out.println("  doctor [--live]          Pre-demo check: key, connection, data, and a rehearsal live search");
        out.println("  profile set --make M --model M --year Y --body suv --km N --city C --province P");
        out.println("              [--trim T] [--transmission automatic] [--fuel gasoline] [--features \"a, b\"]");
        out.println("              [--defect scratch:rear door:minor]");
        out.println("  profile show             Show the current vehicle");
        out.println("  session list|load <id>|delete <id>");
        out.println("  comps search [--source seeded|live|csv] [--csv file] [--radius km] [--max n]");
        out.println("  price [--strategy max-profit|quick-sale] [--margin 0.05] [--spread 0.14] [--baseline N]");
        out.println("        [--accept] [--accept-ai 1,2]   accept the price, optionally with numbered AI proposals");
        out.println("  listing generate [--tone professional|friendly|concise] [--no-features] [--no-maintenance]");
        out.println("                   [--keywords \"a, b\"]");
        out.println("  listing show|override    Show the latest listing, or accept its validation warnings");
        out.println("  export --platform autotrader|kijiji|facebook-marketplace [--out file]");
        out.println("  history import --file report.pdf|report.json [--confirm]");
        out.println("  history set --owners N --accidents N|unknown [--lien none|outstanding|unknown] [--provinces ON]");
        out.println("  forecast [--holding 150]  Value at 3, 6 and 12 months and when waiting stops paying off");
        out.println("  offer add --amount N [--buyer dealer|private] [--notes \"what the buyer said\"]");
        out.println("  offer list");
        out.println("  position                 How the vehicle compares with the comparables, with evidence");
        out.println("  simulate start [--persona serious|low-baller|dealer|suspicious]   Practise with an AI buyer");
        out.println("  simulate say \"message\" | end | show");
        out.println();
        out.println("Options:");
        out.println("  --json       Also print the agent trace as JSON");
        out.println("  --help       Show this help");
        out.println("  --version    Show the version");
    }
}
