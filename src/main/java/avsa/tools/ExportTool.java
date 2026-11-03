package avsa.tools;

import avsa.agent.Tool;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.ExportedListing;
import avsa.domain.GeneratedListing;
import avsa.domain.Platform;
import avsa.services.ExporterFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Formats a listing for one marketplace and optionally saves it (F08). Args: listing, platform, out. */
public final class ExportTool implements Tool {

    public static final String NAME = "export";

    private final ExporterFactory factory;

    public ExportTool(ExporterFactory factory) {
        this.factory = factory;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        GeneratedListing listing = args.require("listing", GeneratedListing.class);
        Platform platform = args.require("platform", Platform.class);
        ExportedListing exported = factory.createExporter(platform).export(listing);
        Path out = args.optional("out", Path.class).orElse(null);
        if (out == null) {
            return ToolResult.success(exported);
        }
        try {
            Files.writeString(out, exported.text(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ToolResult.success(exported, List.of("The file could not be written (" + e.getMessage()
                    + "). Copy the text instead."));
        }
        return ToolResult.success(new ExportedListing(exported.listing(), exported.profile(), exported.fields(),
                exported.text(), exported.warnings(), exported.trimmedSections(), exported.withinLimits(), out));
    }
}
