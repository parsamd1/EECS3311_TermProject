package avsa.services;

import avsa.domain.GeneratedListing;
import avsa.domain.PlatformProfile;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Kijiji: plain text with bulleted lists, and the disclosure straight after the description. */
public final class KijijiExporter extends ListingExporter {

    KijijiExporter(PlatformProfile profile, Clock clock) {
        super(profile, clock);
    }

    @Override
    protected Map<String, String> mapFields(GeneratedListing listing) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(TITLE, listing.title());
        fields.put(PRICE, formatPrice(listing.price()));
        fields.put(DESCRIPTION, listing.description());
        fields.put(DISCLOSURE, "CONDITION\n" + listing.conditionDisclosure());
        if (!listing.highlights().isEmpty()) {
            fields.put(FEATURES, "FEATURES\n" + bullets(listing.highlights()));
        }
        if (!listing.maintenanceDetails().isEmpty()) {
            fields.put(MAINTENANCE, "MAINTENANCE\n" + bullets(listing.maintenanceDetails()));
        }
        if (!listing.seoKeywords().isEmpty()) {
            fields.put(KEYWORDS, "Keywords: " + String.join(", ", listing.seoKeywords()));
        }
        return fields;
    }

    private static String bullets(List<String> items) {
        return items.stream().map(item -> "- " + item).collect(Collectors.joining("\n"));
    }

    @Override
    protected String render(Map<String, String> fields) {
        return fields.get(TITLE) + "\n" + fields.get(PRICE) + "\n\n"
                + body(fields, List.of(DESCRIPTION, DISCLOSURE, FEATURES, MAINTENANCE, KEYWORDS));
    }
}
