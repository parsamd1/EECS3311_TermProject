package avsa.application;

import avsa.agent.AgentTrace;
import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolManager;
import avsa.agent.ToolResult;
import avsa.agent.tasks.ConnectionCheckTask;
import avsa.domain.ChatTurn;
import avsa.domain.CoachingReport;
import avsa.domain.ComparableListing;
import avsa.domain.DepreciationForecast;
import avsa.domain.ExportedListing;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingEdit;
import avsa.domain.ListingOptions;
import avsa.domain.MarketSnapshot;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.Persona;
import avsa.domain.Platform;
import avsa.domain.PositioningReport;
import avsa.domain.PriceAdjustment;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Session;
import avsa.domain.SimulationSession;
import avsa.domain.StrategyType;
import avsa.domain.ValidationError;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.history.HistoryImportResult;
import avsa.infrastructure.history.HistoryInput;
import avsa.infrastructure.market.MarketSourceType;
import avsa.services.BuyerSimulator;
import avsa.services.ForecastInput;
import avsa.services.ListingValidator;
import avsa.services.PricingInput;
import avsa.services.SearchRequest;
import avsa.tools.ExportTool;
import avsa.tools.ForecastTool;
import avsa.tools.HistoryImportTool;
import avsa.tools.ListingTool;
import avsa.tools.MarketSearchTool;
import avsa.tools.OfferEvaluationTool;
import avsa.tools.PositioningTool;
import avsa.tools.PricingTool;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Facade: the single entry point for the GUI and the CLI. It reads and updates the current
 * session, runs features through the tools, and publishes an event after every change.
 */
public final class AgentController {

    private final SessionService sessions;
    private final ToolManager tools;
    private final EventBus events;
    private final AgentTrace trace;
    private final ListingValidator validator;
    private final AiTaskRunner ai;
    private final boolean liveSearchConfigured;
    private final BuyerSimulator simulator;
    private final Clock clock;
    private List<String> lastNotes = List.of();
    private PricingInput lastPricingInput;
    private PriceRecommendation lastRecommendation;

    public AgentController(SessionService sessions, ToolManager tools, EventBus events, AgentTrace trace,
                           ListingValidator validator, AiTaskRunner ai, boolean liveSearchConfigured,
                           BuyerSimulator simulator, Clock clock) {
        this.sessions = sessions;
        this.tools = tools;
        this.events = events;
        this.trace = trace;
        this.validator = validator;
        this.ai = ai;
        this.liveSearchConfigured = liveSearchConfigured;
        this.simulator = simulator;
        this.clock = clock;
    }

    public SessionService sessions() {
        return sessions;
    }

    public AgentTrace trace() {
        return trace;
    }

    public EventBus events() {
        return events;
    }

    /** What the user should know about the last action, such as an AI step that used its fallback. */
    public List<String> lastNotes() {
        return lastNotes;
    }

    /**
     * Saves the profile into the current session, or starts a session for it (F01).
     *
     * @throws IllegalArgumentException the profile has blocking errors
     */
    public VehicleProfile saveProfile(VehicleProfile profile) {
        List<ValidationError> errors = profile.validate(LocalDate.now(clock));
        if (VehicleProfile.hasBlockingErrors(errors)) {
            throw new IllegalArgumentException(errors.stream().filter(ValidationError::blocking)
                    .map(ValidationError::message).reduce((a, b) -> a + " " + b).orElse(""));
        }
        if (sessions.hasCurrent() && sessions.current().activeProfile().sameVehicleAs(profile)) {
            sessions.current().updateProfile(profile);
        } else {
            sessions.create(profile);
        }
        events.publish(new ProfileUpdated(clock.instant(), session().id()));
        return profile;
    }

    /** @param csvFile needed only when {@code source} is CSV */
    public MarketSnapshot findComparables(SearchRequest request, MarketSourceType source, Path csvFile) {
        Session session = session();
        SearchRequest withHistory = request.withHistory(session.history().orElse(null));
        MarketSnapshot snapshot = run(MarketSearchTool.NAME, ToolArgs.of("profile", session.activeProfile(),
                "request", withHistory, "source", source, "csvFile", csvFile)).valueAs(MarketSnapshot.class);
        session.addSnapshot(snapshot);
        events.publish(new MarketDataReady(clock.instant(), session.id()));
        return snapshot;
    }

    /** The seller leaves a comparable out of pricing and positioning, or puts it back (F02). */
    public MarketSnapshot excludeComparable(String listingId, boolean excluded) {
        return changeComparable(listingId, listing -> {
            listing.setExcluded(excluded);
            if (excluded) {
                listing.setPinned(false);
            }
        });
    }

    /** A pinned comparable is never excluded and is ranked first, so positioning always cites it (F02). */
    public MarketSnapshot pinComparable(String listingId, boolean pinned) {
        return changeComparable(listingId, listing -> {
            listing.setPinned(pinned);
            if (pinned) {
                listing.setExcluded(false);
            }
        });
    }

    private MarketSnapshot changeComparable(String listingId, Consumer<ComparableListing> change) {
        Session session = session();
        MarketSnapshot snapshot = session.latestSnapshot().orElseThrow(() ->
                new IllegalStateException("No comparables yet. Find comparables first."));
        ComparableListing listing = snapshot.rankedListings().stream().filter(l -> l.listingId().equals(listingId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No comparable with id " + listingId));
        change.accept(listing);
        List<ComparableListing> ranked = snapshot.rankedListings().stream()
                .sorted(Comparator.comparing((ComparableListing l) -> !l.pinned())
                        .thenComparing(ComparableListing::excluded)
                        .thenComparing(ComparableListing::score, Comparator.reverseOrder()))
                .toList();
        MarketSnapshot updated = snapshot.withListings(ranked);
        session.replaceLatestSnapshot(updated);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "comparables"));
        return updated;
    }

    /** Reads a history report; nothing is stored until the seller confirms it. */
    public HistoryImportResult importHistory(HistoryInput input) {
        return run(HistoryImportTool.NAME, ToolArgs.of("input", input, "profile", session().activeProfile()))
                .valueAs(HistoryImportResult.class);
    }

    public void confirmHistory(VehicleHistory history) {
        session().setHistory(history);
        events.publish(new HistoryUpdated(clock.instant(), session().id()));
    }

    /**
     * Prices the vehicle from the latest market snapshot; the result becomes active only when accepted.
     *
     * @param manualBaseline the seller's own estimate, used only when there are too few comparables; may be null
     */
    public PriceRecommendation recommendPrice(StrategyType strategy, PricingParameters params, Money manualBaseline) {
        Session session = session();
        List<ComparableListing> comps = session.latestSnapshot().map(MarketSnapshot::rankedListings).orElse(List.of());
        PricingInput input = new PricingInput(session.activeProfile(), comps, params, manualBaseline,
                session.history().orElse(null));
        PriceRecommendation rec = run(PricingTool.NAME, ToolArgs.of("input", input, "strategy", strategy))
                .valueAs(PriceRecommendation.class);
        lastPricingInput = input;
        lastRecommendation = rec;
        return rec;
    }

    public void acceptPrice(PriceRecommendation rec) {
        acceptPrice(rec, List.of());
    }

    /**
     * Makes the price active. Accepted AI proposals are applied by re-running the engine, so each one
     * is capped there and the explanation from the first run is kept (F03).
     */
    public PriceRecommendation acceptPrice(PriceRecommendation rec, List<UUID> acceptedAiIds) {
        PriceRecommendation active = rec;
        if (!acceptedAiIds.isEmpty()) {
            if (lastRecommendation == null || !lastRecommendation.id().equals(rec.id())) {
                throw new IllegalStateException("Run pricing again before accepting AI adjustments.");
            }
            List<PriceAdjustment> accepted = rec.proposals().stream().filter(p -> acceptedAiIds.contains(p.id()))
                    .map(p -> new PriceAdjustment(p.id(), p.label(), p.amount(), p.kind(), true)).toList();
            active = run(PricingTool.NAME, ToolArgs.of("input", lastPricingInput.withAiAdjustments(accepted),
                    "strategy", rec.strategy(), "explain", false)).valueAs(PriceRecommendation.class);
            active.setExplanation(rec.explanation().orElse(""), rec.templateExplanation());
        }
        session().setActivePrice(active);
        events.publish(new PriceRecommended(clock.instant(), session().id()));
        return active;
    }

    public DepreciationForecast forecast(Money monthlyHoldingCost) {
        Session session = session();
        PriceRecommendation price = activePrice(session);
        ForecastInput input = new ForecastInput(price.retail().midpoint(), session.activeProfile().year(),
                session.activeProfile().segment(), LocalDate.now(clock), monthlyHoldingCost, price.wholesale().midpoint());
        DepreciationForecast forecast = run(ForecastTool.NAME, ToolArgs.of("input", input))
                .valueAs(DepreciationForecast.class);
        session.setForecast(forecast);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "forecast"));
        return forecast;
    }

    public GeneratedListing generateListing(ListingOptions options) {
        Session session = session();
        GeneratedListing listing = run(ListingTool.NAME, ToolArgs.of("facts", facts(session), "options", options))
                .valueAs(GeneratedListing.class);
        session.addListing(listing);
        events.publish(new ListingGenerated(clock.instant(), session.id()));
        return listing;
    }

    /** Applies the seller's edit and validates the listing again. */
    public GeneratedListing editListing(ListingEdit edit) {
        Session session = session();
        GeneratedListing edited = latestListing(session).withEdit(edit.title(), edit.description(), edit.highlights(),
                edit.maintenanceDetails(), edit.conditionDisclosure());
        GeneratedListing validated = edited.withReport(validator.validate(edited, facts(session)));
        session.addListing(validated);
        events.publish(new ListingGenerated(clock.instant(), session.id()));
        return validated;
    }

    /** The seller explicitly accepts a listing that failed validation, so it can be exported (F07). */
    public GeneratedListing overrideListingValidation() {
        Session session = session();
        GeneratedListing overridden = latestListing(session).withOverride();
        session.addListing(overridden);
        events.publish(new ListingGenerated(clock.instant(), session.id()));
        return overridden;
    }

    /** @param out where to save the text; may be null to only preview it */
    public ExportedListing exportListing(Platform platform, Path out) {
        return run(ExportTool.NAME, ToolArgs.of("listing", latestListing(session()), "platform", platform, "out", out))
                .valueAs(ExportedListing.class);
    }

    /** Compares the vehicle with the latest comparables at the active price (F05). */
    public PositioningReport analyzePositioning() {
        Session session = session();
        List<ComparableListing> comps = session.latestSnapshot().map(MarketSnapshot::rankedListings).orElseThrow(() ->
                new IllegalStateException("No comparables yet. Find comparables first."));
        PositioningReport report = run(PositioningTool.NAME, ToolArgs.of("profile", session.activeProfile(),
                "facts", facts(session), "price", activePrice(session), "comparables", comps))
                .valueAs(PositioningReport.class);
        session.setPositioning(report);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "positioning"));
        return report;
    }

    /** Starts a practice negotiation about the latest listing; the buyer writes first (F09). */
    public SimulationSession startSimulation(Persona persona) {
        Session session = session();
        SimulationSession simulation = simulator.start(persona, latestListing(session), facts(session),
                activePrice(session).walkAwayFloor());
        session.addSimulation(simulation);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "simulation"));
        return simulation;
    }

    /** The seller's reply; returns the buyer's answer. Every turn is saved, so an abandoned chat is kept. */
    public ChatTurn sendSellerMessage(String text) {
        Session session = session();
        ChatTurn buyer = simulator.reply(currentSimulation(session), facts(session), text);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "simulation"));
        return buyer;
    }

    public CoachingReport endSimulation() {
        Session session = session();
        CoachingReport report = simulator.end(currentSimulation(session), facts(session));
        events.publish(new SessionUpdated(clock.instant(), session.id(), "simulation"));
        return report;
    }

    private static SimulationSession currentSimulation(Session session) {
        return session.latestSimulation().orElseThrow(() ->
                new IllegalStateException("No practice negotiation yet. Start one first."));
    }

    public OfferEvaluation evaluateOffer(Offer offer) {
        Session session = session();
        OfferEvaluation evaluation = run(OfferEvaluationTool.NAME, ToolArgs.of("offer", offer,
                "price", activePrice(session), "forecast", session.forecast().orElse(null),
                "comparables", session.latestSnapshot().map(MarketSnapshot::rankedListings).orElse(List.of())))
                .valueAs(OfferEvaluation.class);
        session.addOffer(evaluation);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "offers"));
        return evaluation;
    }

    /** Whether live search can run: it needs an API key. */
    public boolean liveSearchConfigured() {
        return liveSearchConfigured;
    }

    /** Sends the smallest request to the model; false with the reason in {@link #lastNotes()} when it fails. */
    public boolean checkAiConnection() {
        AiResult<String> result = ai.run(new ConnectionCheckTask());
        lastNotes = result.notes();
        return !result.usedFallback();
    }

    private ToolResult run(String tool, ToolArgs args) {
        ToolResult result = tools.execute(tool, args);
        lastNotes = result.notes();
        if (!result.ok()) {
            throw new IllegalStateException(result.error());
        }
        return result;
    }

    /** @throws IllegalStateException no profile yet; features need one first (F01) */
    private Session session() {
        return sessions.current();
    }

    private static PriceRecommendation activePrice(Session session) {
        return session.activePrice().orElseThrow(() ->
                new IllegalStateException("No active price yet. Run pricing and accept a price first."));
    }

    private static GeneratedListing latestListing(Session session) {
        return session.latestListing().orElseThrow(() ->
                new IllegalStateException("No listing yet. Generate a listing first."));
    }

    private static VehicleFacts facts(Session session) {
        return VehicleFacts.from(session.activeProfile(), session.history().orElse(null),
                activePrice(session).askingPrice());
    }

    Optional<Session> currentSession() {
        return sessions.hasCurrent() ? Optional.of(sessions.current()) : Optional.empty();
    }
}
