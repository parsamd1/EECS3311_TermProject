package avsa.agent;

/** Command: one action the agent can take, run by name through {@link ToolManager}. */
public interface Tool {

    String name();

    ToolResult execute(ToolArgs args);
}
