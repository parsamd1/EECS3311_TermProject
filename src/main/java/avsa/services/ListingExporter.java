package avsa.services;

import avsa.domain.ExportedListing;
import avsa.domain.GeneratedListing;
import avsa.domain.Money;
import avsa.domain.PlatformProfile;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats a listing for one marketplace (F08). Template Method: {@link #export} is final and always
 * runs the same safety sequence; a subclass supplies only {@link #mapFields} and {@link #render}.
 */
public abstract class ListingExporter {

    public static final String TITLE = "title";
    public static final String PRICE = "price";
    public static final String DESCRIPTION = "description";
    public static final String DISCLOSURE = "conditionDisclosure";
    public static final String FEATURES = "features";
    public static final String MAINTENANCE = "maintenance";
    public static final String KEYWORDS = "keywords";
    /** The limit name for all the text that goes in the platform's description box. */
    public static final String BODY = "body";

    /** Removed first to last when the text is too long. The disclosure and price are never removed. */
    private static final List<String> TRIM_ORDER = List.of(KEYWORDS, FEATURES, MAINTENANCE);
    private static final List<String> BODY_FIELDS = List.of(DESCRIPTION, DISCLOSURE, MAINTENANCE, FEATURES, KEYWORDS);
    private static final String SECTION_BREAK = "\n\n";
    private static final int VERIFICATION_MAX_AGE_DAYS = 180;

    protected final PlatformProfile profile;
    private final Clock clock;
    private List<String> warnings;
    private List<String> trimmed;
    private boolean withinLimits;

    protected ListingExporter(PlatformProfile profile, Clock clock) {
        this.profile = profile;
        this.clock = clock;
    }

    /** @throws ExportBlockedException the listing failed validation, a required field is missing, or a check failed */
    public final ExportedListing export(GeneratedListing listing) {
        warnings = new ArrayList<>();
        trimmed = new ArrayList<>();
        withinLimits = true;
        checkPrerequisites(listing);
        Map<String, String> fields = applyLimits(mapFields(listing));
        String text = render(fields);
        verifyDisclosure(listing, fields, text);
        warnAboutVerification();
        return new ExportedListing(listing, profile, fields, text, warnings, trimmed, withinLimits, null);
    }

    protected void checkPrerequisites(GeneratedListing listing) {
        if (!listing.isExportable()) {
            throw new ExportBlockedException("This listing failed validation. Fix it, or explicitly override the "
                    + "warnings, before exporting.");
        }
        for (String field : profile.requiredFields()) {
            if (valueOf(listing, field).isBlank()) {
                throw new ExportBlockedException(profile.platform().displayName() + " requires the field: " + field + ".");
            }
        }
        if (listing.conditionDisclosure().isBlank()) {
            throw new ExportBlockedException("The listing has no condition disclosure.");
        }
    }

    private static String valueOf(GeneratedListing listing, String field) {
        return switch (field) {
            case TITLE -> listing.title();
            case PRICE -> listing.price().toString();
            case DESCRIPTION -> listing.description();
            case DISCLOSURE -> listing.conditionDisclosure();
            default -> throw new IllegalStateException("Unknown required field in the platform profile: " + field);
        };
    }

    /** The platform's fields, using the names above. Sections with nothing in them are left out. */
    protected abstract Map<String, String> mapFields(GeneratedListing listing);

    /** Removes optional sections in the fixed order until the text fits; warns if it still does not. */
    protected Map<String, String> applyLimits(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>(fields);
        int bodyLimit = profile.limitOf(BODY).orElse(Integer.MAX_VALUE);
        for (String section : TRIM_ORDER) {
            if (bodyLength(result) > bodyLimit && result.remove(section) != null) {
                trimmed.add(section);
            }
        }
        if (bodyLength(result) > bodyLimit) {
            withinLimits = false;
            warnings.add("The text has " + bodyLength(result) + " characters and " + profile.platform().displayName()
                    + " allows " + bodyLimit + ". Use Make shorter.");
        }
        int titleLimit = profile.limitOf(TITLE).orElse(Integer.MAX_VALUE);
        if (result.getOrDefault(TITLE, "").length() > titleLimit) {
            withinLimits = false;
            warnings.add("The title has " + result.get(TITLE).length() + " characters and "
                    + profile.platform().displayName() + " allows " + titleLimit + ". Use Make shorter.");
        }
        return result;
    }

    /** All the text for the platform's description box, in the given section order. */
    protected static String body(Map<String, String> fields, List<String> order) {
        return order.stream().filter(fields::containsKey).map(fields::get)
                .reduce((a, b) -> a + SECTION_BREAK + b).orElse("");
    }

    /** Characters in the platform's description box: every body section and the breaks between them. */
    public static int bodyLength(Map<String, String> fields) {
        return body(fields, BODY_FIELDS).length();
    }

    protected abstract String render(Map<String, String> fields);

    /** The final check: however a subclass rendered it, the disclosure and price must be in the output. */
    protected void verifyDisclosure(GeneratedListing listing, Map<String, String> fields, String text) {
        if (!text.contains(listing.conditionDisclosure())) {
            throw new ExportBlockedException("Export stopped: the condition disclosure is missing from the output.");
        }
        String price = formatPrice(listing.price());
        if (!price.equals(fields.get(PRICE)) || !text.contains(price)) {
            throw new ExportBlockedException("Export stopped: the price is missing from the output.");
        }
    }

    private void warnAboutVerification() {
        LocalDate verified = profile.lastVerified();
        String platform = profile.platform().displayName();
        if (verified == null) {
            warnings.add(platform + "'s limits have not been verified. Check them on its posting form.");
        } else if (verified.isBefore(LocalDate.now(clock).minusDays(VERIFICATION_MAX_AGE_DAYS))) {
            warnings.add(platform + "'s limits were last verified on " + verified + ".");
        }
    }

    protected final String formatPrice(Money price) {
        return new DecimalFormat(profile.priceFormat(), DecimalFormatSymbols.getInstance(Locale.ROOT))
                .format(price.amount());
    }
}
