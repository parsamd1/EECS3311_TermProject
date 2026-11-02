package avsa.agent;

/** What a trace entry records. */
public enum TraceKind {
    PROMPT,
    RESPONSE,
    REJECTED,
    ERROR,
    FALLBACK,
    TOOL_CALL
}
