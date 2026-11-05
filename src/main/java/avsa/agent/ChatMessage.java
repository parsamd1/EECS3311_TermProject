package avsa.agent;

/** One turn of a conversation as the model sees it: who spoke ("buyer" or "seller") and what was said. */
public record ChatMessage(String role, String text) {
}
