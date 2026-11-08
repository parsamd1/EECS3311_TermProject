package avsa.application;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.ClaudeClient;
import avsa.agent.LLMClient;
import avsa.agent.MemoryManager;
import avsa.agent.MockLLMClient;
import avsa.agent.PlanValidator;
import avsa.agent.Planner;
import avsa.agent.StepCatalog;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.agent.ToolManager;
import avsa.infrastructure.AppConfig;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.JsonSessionRepository;
import avsa.infrastructure.PlatformProfileLoader;
import avsa.infrastructure.SchemaMigrator;
import avsa.infrastructure.history.CarfaxPdfAdapter;
import avsa.infrastructure.history.HistoryConsistencyChecker;
import avsa.infrastructure.history.HistoryImportService;
import avsa.infrastructure.history.JsonHistoryAdapter;
import avsa.infrastructure.history.ManualHistoryImporter;
import avsa.infrastructure.history.PdfTextExtractor;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.ListingCache;
import avsa.infrastructure.market.LiveSearchSource;
import avsa.infrastructure.market.MarketSources;
import avsa.services.BuyerSimulator;
import avsa.services.ClaimValidator;
import avsa.services.DepreciationCalculator;
import avsa.services.DepreciationRateTable;
import avsa.services.ExporterFactory;
import avsa.services.ListingValidator;
import avsa.services.OfferEvaluator;
import avsa.services.PositioningAnalyzer;
import avsa.services.PricingEngine;
import avsa.services.TemplateListingWriter;
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
import java.util.List;

/** Builds the whole application once, for the GUI and the CLI alike. */
public final class AvsaApplication {

    public static final Path SEEDED_DATASET = Path.of("data", "listings.csv");

    private AvsaApplication() {
    }

    /**
     * Without an API key the AI steps use their labelled fallbacks and live search falls back
     * to the seeded dataset.
     *
     * @param llm the model to use; null to use Claude when a key is configured
     */
    public static AgentController create(AppConfig config, Path dataDir, Clock clock, LLMClient llm) {
        AgentTrace trace = new AgentTrace(clock);
        LLMClient client = llm != null ? llm : config.llmApiKey().isPresent() ? new ClaudeClient(config)
                : new MockLLMClient().alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE,
                        "No API key is set (copy .env.example to .env and add one).");
        AiTaskRunner ai = new AiTaskRunner(new PromptComposer(), client, new ResponseParser(new JsonMapper()), trace);
        boolean liveConfigured = llm != null || config.llmApiKey().isPresent();
        LiveSearchSource live = liveConfigured ? new LiveSearchSource(ai, clock) : new LiveSearchSource();
        MarketSources sources = new MarketSources(SEEDED_DATASET, live,
                new ListingCache(dataDir.resolve("cache").resolve("listings.json"), new JsonMapper()), clock);
        Gazetteer gazetteer = new Gazetteer();
        ListingValidator validator = new ListingValidator(clock);

        ToolManager tools = new ToolManager(trace);
        tools.register(new MarketSearchTool(ai, sources, gazetteer, clock));
        tools.register(new HistoryImportTool(new HistoryImportService(List.of(
                new CarfaxPdfAdapter(new PdfTextExtractor()), new JsonHistoryAdapter(new JsonMapper()),
                new ManualHistoryImporter()), new HistoryConsistencyChecker())));
        tools.register(new PricingTool(ai, new PricingEngine(clock)));
        tools.register(new ForecastTool(new DepreciationCalculator(
                DepreciationRateTable.loadOrDefault(DepreciationRateTable.DEFAULT_FILE))));
        tools.register(new ListingTool(ai, new TemplateListingWriter(), validator));
        tools.register(new ExportTool(new ExporterFactory(new PlatformProfileLoader(), clock)));
        tools.register(new OfferEvaluationTool(ai, new OfferEvaluator()));
        tools.register(new PositioningTool(ai, new PositioningAnalyzer(), new ClaimValidator()));

        SessionService sessions = new SessionService(
                new JsonSessionRepository(dataDir.resolve("sessions"), new JsonMapper(), new SchemaMigrator()), clock);
        EventBus events = new EventBus();
        StepCatalog catalog = new StepCatalog();
        events.subscribe(AgentEvent.class, new AutoSaveListener(sessions));
        return new AgentController(sessions, tools, events, trace, validator, ai, liveConfigured,
                new BuyerSimulator(ai, new MemoryManager(), clock),
                new Planner(ai, new PlanValidator(catalog), catalog), clock);
    }
}
