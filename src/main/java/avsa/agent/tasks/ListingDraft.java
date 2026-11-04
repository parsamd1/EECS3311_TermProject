package avsa.agent.tasks;

import java.util.List;

/** A listing as the AI wrote it, before validation. */
public record ListingDraft(String title, String description, List<String> highlights, String conditionDisclosure,
                           List<String> seoKeywords) {
}
