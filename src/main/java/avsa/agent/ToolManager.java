package avsa.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Invoker: runs any registered tool by name, records the call in the trace, and turns
 * a tool's exception into a failed result so callers handle one shape.
 */
public final class ToolManager {

    private final Map<String, Tool> registry = new LinkedHashMap<>();
    private final AgentTrace trace;

    public ToolManager(AgentTrace trace) {
        this.trace = trace;
    }

    public void register(Tool tool) {
        if (registry.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("A tool is already registered as " + tool.name());
        }
    }

    public Set<String> names() {
        return registry.keySet();
    }

    public ToolResult execute(String name, ToolArgs args) {
        Tool tool = registry.get(name);
        ToolResult result;
        if (tool == null) {
            result = ToolResult.failure("Unknown tool: " + name);
        } else {
            try {
                result = tool.execute(args);
            } catch (RuntimeException e) {
                result = ToolResult.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        trace.recordToolCall(name, args, result);
        return result;
    }
}
