package avsa.tools;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.agent.tasks.CompReview;
import avsa.agent.tasks.CompReviewTask;
import avsa.domain.MarketSnapshot;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.ListingNormalizer;
import avsa.infrastructure.market.MarketSourceType;
import avsa.infrastructure.market.MarketSources;
import avsa.services.ComparableFinder;
import avsa.services.OutlierFilter;
import avsa.services.SearchRequest;
import avsa.services.SimilarityScorer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds and ranks comparables, then has the AI review the listings' text (F02).
 * Args: profile, request, source (default seeded), csvFile (for a CSV source).
 */
public final class MarketSearchTool extends AiAssistedTool {

    public static final String NAME = "market_search";

    private final MarketSources sources;
    private final Gazetteer gazetteer;
    private final Clock clock;

    public MarketSearchTool(AiTaskRunner ai, MarketSources sources, Gazetteer gazetteer, Clock clock) {
        super(ai);
        this.sources = sources;
        this.gazetteer = gazetteer;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        VehicleProfile profile = args.require("profile", VehicleProfile.class);
        SearchRequest request = args.optional("request", SearchRequest.class).orElse(SearchRequest.defaults());
        MarketSourceType type = args.optional("source", MarketSourceType.class).orElse(MarketSourceType.SEEDED);
        Path csv = args.optional("csvFile", Path.class).orElse(null);

        ComparableFinder finder = new ComparableFinder(sources.forChoice(type, csv), new ListingNormalizer(gazetteer),
                gazetteer, new OutlierFilter(), new SimilarityScorer(), clock);
        MarketSnapshot snapshot = finder.find(profile, request);

        List<String> notes = new ArrayList<>();
        if (!snapshot.rankedListings().isEmpty()) {
            AiResult<CompReview> review = ai.run(new CompReviewTask(snapshot.rankedListings(), profile.trim()));
            if (review.usedFallback()) {
                notes.add("The AI review was unavailable, so the listings are unreviewed.");
            } else {
                snapshot = finder.applyReview(snapshot, CompReviewTask.byListing(review.value()));
            }
        }
        return ToolResult.success(snapshot, notes);
    }
}
