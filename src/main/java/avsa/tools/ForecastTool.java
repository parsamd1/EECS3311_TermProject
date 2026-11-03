package avsa.tools;

import avsa.agent.Tool;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.services.DepreciationCalculator;
import avsa.services.ForecastInput;

/** Forecasts the cost of waiting (F06). Args: input. */
public final class ForecastTool implements Tool {

    public static final String NAME = "forecast";

    private final DepreciationCalculator calculator;

    public ForecastTool(DepreciationCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolResult execute(ToolArgs args) {
        return ToolResult.success(calculator.forecast(args.require("input", ForecastInput.class)));
    }
}
