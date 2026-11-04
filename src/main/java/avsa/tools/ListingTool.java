package avsa.tools;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.agent.tasks.ListingDraft;
import avsa.agent.tasks.ListingDraftTask;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingOptions;
import avsa.domain.VehicleFacts;
import avsa.services.ListingValidator;
import avsa.services.TemplateListingWriter;
import java.util.List;

/**
 * Writes and validates a listing (F07): the AI drafts it and the validator checks it. If no draft
 * passes, the best one is kept with its violations; if the AI is unavailable, a template listing
 * is written and labelled. Args: facts, options.
 */
public final class ListingTool extends AiAssistedTool {

    public static final String NAME = "listing";

    private final TemplateListingWriter writer;
    private final ListingValidator validator;

    public ListingTool(AiTaskRunner ai, TemplateListingWriter writer, ListingValidator validator) {
        super(ai);
        this.writer = writer;
        this.validator = validator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        VehicleFacts facts = args.require("facts", VehicleFacts.class);
        ListingOptions options = args.optional("options", ListingOptions.class).orElse(ListingOptions.defaults());
        ListingDraftTask task = new ListingDraftTask(facts, options, validator, writer);
        AiResult<ListingDraft> result = ai.run(task);
        if (!result.usedFallback()) {
            GeneratedListing listing = task.toListing(result.value());
            return ToolResult.success(listing.withReport(validator.validate(listing, facts)));
        }
        if (task.bestDraft().isPresent()) {
            return ToolResult.success(task.bestDraft().get(), List.of("No AI draft passed validation after "
                    + task.maxAttempts() + " attempts; this is the closest one. Fix the marked problems or "
                    + "override them before exporting."));
        }
        GeneratedListing template = writer.write(facts, options);
        return ToolResult.success(template.withReport(validator.validate(template, facts)),
                List.of("The AI was unavailable, so a plain template listing was written."));
    }
}
