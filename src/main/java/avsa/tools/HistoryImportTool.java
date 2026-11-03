package avsa.tools;

import avsa.agent.Tool;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.history.HistoryImportService;
import avsa.infrastructure.history.HistoryInput;

/** Imports a vehicle history report (F04). Args: input, profile. */
public final class HistoryImportTool implements Tool {

    public static final String NAME = "history_import";

    private final HistoryImportService service;

    public HistoryImportTool(HistoryImportService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        return ToolResult.success(service.importFrom(args.require("input", HistoryInput.class),
                args.require("profile", VehicleProfile.class)));
    }
}
