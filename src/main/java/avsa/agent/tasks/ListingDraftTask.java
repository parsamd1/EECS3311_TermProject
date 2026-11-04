package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingOptions;
import avsa.domain.ValidationReport;
import avsa.domain.VehicleFacts;
import avsa.domain.Violation;
import avsa.services.ListingValidator;
import avsa.services.TemplateListingWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asks the AI to write the listing (F07). Every draft is checked by {@link ListingValidator};
 * a failing draft is sent back with its violations, up to three attempts in all. The best draft
 * is kept, so the seller can still fix or override it.
 */
public final class ListingDraftTask implements AiTask<ListingDraft> {

    static final int ATTEMPTS = 3;

    private final VehicleFacts facts;
    private final ListingOptions options;
    private final ListingValidator validator;
    private final TemplateListingWriter writer;
    private GeneratedListing best;

    public ListingDraftTask(VehicleFacts facts, ListingOptions options, ListingValidator validator,
                            TemplateListingWriter writer) {
        this.facts = facts;
        this.options = options;
        this.validator = validator;
        this.writer = writer;
    }

    @Override
    public String name() {
        return "listing-draft";
    }

    @Override
    public int maxAttempts() {
        return ATTEMPTS;
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.listing(facts, options);
    }

    @Override
    public Class<ListingDraft> resultType() {
        return ListingDraft.class;
    }

    @Override
    public List<String> validate(ListingDraft draft) {
        if (draft.title() == null || draft.description() == null || draft.conditionDisclosure() == null) {
            return List.of("The JSON needs a title, a description and a conditionDisclosure.");
        }
        GeneratedListing listing = toListing(draft);
        ValidationReport report = validator.validate(listing, facts);
        GeneratedListing checked = listing.withReport(report);
        if (best == null || report.violations().size() < best.report().orElseThrow().violations().size()) {
            best = checked;
        }
        return report.violations().stream().map(Violation::message).toList();
    }

    /** The maintenance lines come from the records, never from the AI, so service history cannot be invented. */
    public GeneratedListing toListing(ListingDraft draft) {
        GeneratedListing template = writer.write(facts, options);
        List<String> keywords = new ArrayList<>(draft.seoKeywords() == null ? List.of() : draft.seoKeywords());
        options.keywords().stream().filter(k -> !keywords.contains(k)).forEach(keywords::add);
        return new GeneratedListing(draft.title().strip(), draft.description().strip(),
                options.includeFeatures() && draft.highlights() != null ? draft.highlights() : List.of(),
                template.maintenanceDetails(), draft.conditionDisclosure().strip(), facts.price(), keywords,
                options.tone(), false, false, false, null);
    }

    /** The draft with the fewest violations, when the AI answered but no draft passed. */
    public Optional<GeneratedListing> bestDraft() {
        return Optional.ofNullable(best);
    }

    /** Only reached when no draft passed; the caller prefers {@link #bestDraft()} if there is one. */
    @Override
    public ListingDraft fallback() {
        GeneratedListing t = writer.write(facts, options);
        return new ListingDraft(t.title(), t.description(), t.highlights(), t.conditionDisclosure(), t.seoKeywords());
    }
}
