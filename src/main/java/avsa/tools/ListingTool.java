package avsa.tools;

import avsa.agent.AiTaskRunner;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingOptions;
import avsa.domain.VehicleFacts;
import avsa.services.ListingValidator;
import avsa.services.TemplateListingWriter;

/**
 * Writes and validates a listing (F07). The AI draft with its regenerate loop is added with
 * the listing AI work; until then the template writer is used. Args: facts, options.
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
        GeneratedListing draft = writer.write(facts, options);
        return ToolResult.success(draft.withReport(validator.validate(draft, facts)));
    }
}
