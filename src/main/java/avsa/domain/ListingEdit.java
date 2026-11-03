package avsa.domain;

import java.util.List;
import java.util.Objects;

/** The seller's changes to a listing's text (F07). */
public record ListingEdit(String title, String description, List<String> highlights, List<String> maintenanceDetails,
                          String conditionDisclosure) {

    public ListingEdit {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(conditionDisclosure, "conditionDisclosure");
        highlights = List.copyOf(highlights);
        maintenanceDetails = List.copyOf(maintenanceDetails);
    }
}
