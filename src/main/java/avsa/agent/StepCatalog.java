package avsa.agent;

import avsa.agent.tasks.PlanProposal;
import avsa.domain.Platform;
import avsa.domain.StepType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fixed list of steps a guided workflow may use (F12), with what each needs first and which ones
 * stop for the seller's approval. The AI can only choose from this list.
 */
public final class StepCatalog {

    private record Entry(String description, List<StepType> prerequisites, boolean checkpoint) {
    }

    private final Map<StepType, Entry> entries = new EnumMap<>(StepType.class);

    public StepCatalog() {
        entries.put(StepType.FIND_COMPARABLES, new Entry("find and score comparable listings near the seller",
                List.of(), false));
        entries.put(StepType.RECOMMEND_PRICE, new Entry("compute wholesale and retail ranges, the asking price and "
                + "the walk-away floor for a strategy", List.of(StepType.FIND_COMPARABLES), true));
        entries.put(StepType.FORECAST, new Entry("project the value at 3, 6 and 12 months and when waiting stops "
                + "paying off", List.of(StepType.RECOMMEND_PRICE), false));
        entries.put(StepType.ANALYZE_POSITIONING, new Entry("compare the vehicle with the top comparables: "
                + "advantages, weaknesses and talking points", List.of(StepType.RECOMMEND_PRICE), false));
        entries.put(StepType.GENERATE_LISTING, new Entry("write and validate the listing text",
                List.of(StepType.RECOMMEND_PRICE), true));
        entries.put(StepType.EXPORT_LISTING, new Entry("format the listing for the chosen marketplaces",
                List.of(StepType.GENERATE_LISTING), false));
    }

    public boolean isRegistered(StepType type) {
        return entries.containsKey(type);
    }

    /** A step name as the AI wrote it, such as "RECOMMEND_PRICE" or "recommend price". */
    public Optional<StepType> lookup(String name) {
        String key = name == null ? "" : name.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
        for (StepType type : entries.keySet()) {
            if (type.name().equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public List<StepType> prerequisitesOf(StepType type) {
        return entries.get(type).prerequisites();
    }

    public boolean isCheckpoint(StepType type) {
        return entries.get(type).checkpoint();
    }

    public String describe(StepType type) {
        return entries.get(type).description();
    }

    public List<StepType> steps() {
        return List.copyOf(entries.keySet());
    }

    /**
     * The plan used when the AI is unavailable or its answer stays malformed: every step, with the strategy and
     * platforms read from the goal's words.
     */
    public PlanProposal templatePlanFor(String goal) {
        String g = goal.toLowerCase(Locale.ROOT);
        String strategy = g.matches(".*\\b(quick|quickly|fast|asap|dealer|trade.?in|soon)\\b.*") ? "QUICK_SALE"
                : "MAX_PROFIT";
        List<String> platforms = new ArrayList<>();
        if (g.contains("autotrader")) {
            platforms.add(Platform.AUTOTRADER.name());
        }
        if (g.contains("kijiji")) {
            platforms.add(Platform.KIJIJI.name());
        }
        if (g.contains("facebook") || g.contains("marketplace")) {
            platforms.add(Platform.FACEBOOK_MARKETPLACE.name());
        }
        List<String> steps = new ArrayList<>();
        for (StepType type : entries.keySet()) {
            if (type != StepType.EXPORT_LISTING || !platforms.isEmpty()) {
                steps.add(type.name());
            }
        }
        return new PlanProposal(steps, strategy, platforms, g.contains("live") || g.contains("today"), null, null);
    }
}
