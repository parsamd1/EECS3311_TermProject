package avsa.agent;

import java.util.ArrayList;
import java.util.List;

/** The turns of one conversation, and a summary of the ones that no longer fit in the window. */
public final class ConversationMemory {

    private final List<ChatMessage> turns = new ArrayList<>();
    private String summary = "";

    List<ChatMessage> turns() {
        return turns;
    }

    String summary() {
        return summary;
    }

    void setSummary(String newSummary) {
        this.summary = newSummary;
    }
}
