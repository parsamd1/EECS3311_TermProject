package avsa.application;

import avsa.agent.AgentTrace;
import avsa.agent.ToolArgs;
import avsa.agent.ToolManager;
import avsa.agent.ToolResult;
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
import avsa.domain.Platform;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Session;
import avsa.domain.StrategyType;
import avsa.domain.ValidationError;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.history.HistoryImportResult;
import avsa.infrastructure.history.HistoryInput;
import avsa.infrastructure.market.MarketSourceType;
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
import avsa.tools.PricingTool;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    private final Clock clock;
    private List<String> lastNotes = List.of();

    public AgentController(SessionService sessions, ToolManager tools, EventBus events, AgentTrace trace,
                           ListingValidator validator, Clock clock) {
        this.sessions = sessions;
        this.tools = tools;
        this.events = events;
        this.trace = trace;
        this.validator = validator;
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
        return run(PricingTool.NAME, ToolArgs.of("input", input, "strategy", strategy))
                .valueAs(PriceRecommendation.class);
    }

    public void acceptPrice(PriceRecommendation rec) {
        session().setActivePrice(rec);
        events.publish(new PriceRecommended(clock.instant(), session().id()));
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

    public OfferEvaluation evaluateOffer(Offer offer) {
        Session session = session();
        OfferEvaluation evaluation = run(OfferEvaluationTool.NAME, ToolArgs.of("offer", offer,
                "price", activePrice(session), "forecast", session.forecast().orElse(null)))
                .valueAs(OfferEvaluation.class);
        session.addOffer(evaluation);
        events.publish(new SessionUpdated(clock.instant(), session.id(), "offers"));
        return evaluation;
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
