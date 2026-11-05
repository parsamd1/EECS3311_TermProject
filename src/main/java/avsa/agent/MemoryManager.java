package avsa.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps conversations within the model's context: the latest turns are kept word for word and
 * older ones are folded into a short summary (F09).
 */
public final class MemoryManager {

    public static final int DEFAULT_MAX_CONTEXT_TURNS = 12;
    private static final int SUMMARY_CHARS_PER_TURN = 80;

    private final int maxContextTurns;
    private final Map<UUID, ConversationMemory> conversations = new HashMap<>();

    public MemoryManager() {
        this(DEFAULT_MAX_CONTEXT_TURNS);
    }

    public MemoryManager(int maxContextTurns) {
        this.maxContextTurns = maxContextTurns;
    }

    public void append(UUID conversationId, ChatMessage turn) {
        ConversationMemory memory = conversations.computeIfAbsent(conversationId, id -> new ConversationMemory());
        memory.turns().add(turn);
        while (memory.turns().size() > maxContextTurns) {
            ChatMessage oldest = memory.turns().remove(0);
            String line = oldest.role() + ": " + shorten(oldest.text());
            memory.setSummary(memory.summary().isEmpty() ? line : memory.summary() + " | " + line);
        }
    }

    /** The summary of older turns, if any, followed by the recent turns. */
    public List<ChatMessage> window(UUID conversationId) {
        ConversationMemory memory = conversations.get(conversationId);
        if (memory == null) {
            return List.of();
        }
        List<ChatMessage> window = new ArrayList<>();
        if (!memory.summary().isEmpty()) {
            window.add(new ChatMessage("summary", "Earlier in the conversation: " + memory.summary()));
        }
        window.addAll(memory.turns());
        return window;
    }

    public boolean knows(UUID conversationId) {
        return conversations.containsKey(conversationId);
    }

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ').strip();
        return oneLine.length() <= SUMMARY_CHARS_PER_TURN ? oneLine : oneLine.substring(0, SUMMARY_CHARS_PER_TURN) + "...";
    }
}
