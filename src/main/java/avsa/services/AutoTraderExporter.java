package avsa.services;

import avsa.domain.GeneratedListing;
import avsa.domain.PlatformProfile;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AutoTrader: labelled sections, with the seller's records after the features. */
public final class AutoTraderExporter extends ListingExporter {

    AutoTraderExporter(PlatformProfile profile, Clock clock) {
        super(profile, clock);
    }

    @Override
    protected Map<String, String> mapFields(GeneratedListing listing) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(TITLE, listing.title());
        fields.put(PRICE, formatPrice(listing.price()));
        fields.put(DESCRIPTION, listing.description());
        fields.put(DISCLOSURE, "Condition disclosure: " + listing.conditionDisclosure());
        if (!listing.highlights().isEmpty()) {
            fields.put(FEATURES, "Features: " + String.join(", ", listing.highlights()));
        }
        if (!listing.maintenanceDetails().isEmpty()) {
            fields.put(MAINTENANCE, "Maintenance: " + String.join("; ", listing.maintenanceDetails()));
        }
        if (!listing.seoKeywords().isEmpty()) {
            fields.put(KEYWORDS, "Keywords: " + String.join(", ", listing.seoKeywords()));
        }
        return fields;
    }

    @Override
    protected String render(Map<String, String> fields) {
        return "Title: " + fields.get(TITLE) + "\nPrice: " + fields.get(PRICE) + "\n\n"
                + body(fields, List.of(DESCRIPTION, FEATURES, MAINTENANCE, DISCLOSURE, KEYWORDS));
    }
}
