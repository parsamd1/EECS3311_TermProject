package avsa.services;

import avsa.domain.GeneratedListing;
import avsa.domain.PlatformProfile;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Facebook Marketplace: short and conversational, with the disclosure before the optional details. */
public final class FacebookMarketplaceExporter extends ListingExporter {

    FacebookMarketplaceExporter(PlatformProfile profile, Clock clock) {
        super(profile, clock);
    }

    @Override
    protected Map<String, String> mapFields(GeneratedListing listing) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(TITLE, listing.title());
        fields.put(PRICE, formatPrice(listing.price()));
        fields.put(DESCRIPTION, listing.description());
        fields.put(DISCLOSURE, "Please note: " + listing.conditionDisclosure());
        if (!listing.highlights().isEmpty()) {
            fields.put(FEATURES, "Includes: " + String.join(", ", listing.highlights()));
        }
        if (!listing.maintenanceDetails().isEmpty()) {
            fields.put(MAINTENANCE, "Service history: " + String.join("; ", listing.maintenanceDetails()));
        }
        if (!listing.seoKeywords().isEmpty()) {
            fields.put(KEYWORDS, String.join(", ", listing.seoKeywords()));
        }
        return fields;
    }

    @Override
    protected String render(Map<String, String> fields) {
        return fields.get(TITLE) + " | " + fields.get(PRICE) + "\n\n"
                + body(fields, List.of(DESCRIPTION, DISCLOSURE, FEATURES, MAINTENANCE, KEYWORDS));
    }
}
