package avsa.agent;

import java.util.List;
import java.util.Set;

/**
 * What an AI task produced.
 *
 * @param usedFallback true when the deterministic fallback was used; the interface labels it
 * @param notes        why earlier attempts were rejected, or why the fallback was used
 * @param sources      web pages behind the result, which must be shown with it
 */
public record AiResult<T>(T value, boolean usedFallback, List<String> notes, Set<String> sources) {

    public AiResult {
        notes = List.copyOf(notes);
        sources = Set.copyOf(sources);
    }
}
