package avsa.services;

import avsa.domain.Defect;
import avsa.domain.GeneratedListing;
import avsa.domain.Money;
import avsa.domain.ValidationReport;
import avsa.domain.ValidationRule;
import avsa.domain.VehicleFacts;
import avsa.domain.Violation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a listing against the facts (F07): every defect disclosed, no banned claims,
 * facts and price correct, length within bounds. The same check runs on AI drafts,
 * template listings and the seller's edits.
 */
public final class ListingValidator {

    private static final List<String> BANNED_PHRASES = List.of("flawless", "no issues", "no problems",
            "perfect condition", "mint condition", "like new");
    /** Only allowed when the accident history has been confirmed clean. */
    private static final List<String> ACCIDENT_FREE_PHRASES = List.of("accident-free", "accident free", "no accidents",
            "never been in an accident", "never in an accident");
    private static final int MIN_LENGTH = 100;
    private static final int MAX_LENGTH = 4000;
    private static final Pattern KILOMETRES =
            Pattern.compile("(\\d[\\d,]*)(k?)\\s?(?:km|kms|kilometres|kilometers)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOLLARS = Pattern.compile("\\$\\s?(\\d[\\d,]*)");
    /** Dollar amounts well below the price are usually something else, such as the cost of a repair. */
    private static final double PRICE_LIKE_SHARE = 0.5;

    private final List<String> bannedPhrases;
    private final int minLength;
    private final int maxLength;
    private final Clock clock;

    public ListingValidator(Clock clock) {
        this(BANNED_PHRASES, MIN_LENGTH, MAX_LENGTH, clock);
    }

    public ListingValidator(List<String> bannedPhrases, int minLength, int maxLength, Clock clock) {
        this.bannedPhrases = List.copyOf(bannedPhrases);
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.clock = clock;
    }

    public ValidationReport validate(GeneratedListing listing, VehicleFacts facts) {
        List<Violation> violations = new ArrayList<>();
        checkDisclosure(listing, facts, violations);
        checkBannedClaims(listing, facts, violations);
        checkFacts(listing, facts, violations);
        checkLength(listing, violations);
        return new ValidationReport(clock.instant(), violations);
    }

    /** Checked in the disclosure section alone, because that is the part export guarantees to keep. */
    private static void checkDisclosure(GeneratedListing listing, VehicleFacts facts, List<Violation> violations) {
        String disclosure = lower(listing.conditionDisclosure());
        for (Defect defect : facts.defects()) {
            String type = defect.type().name().toLowerCase(Locale.ROOT);
            String location = defect.bodyLocation().toLowerCase(Locale.ROOT);
            if (!disclosure.contains(type) || !disclosure.contains(location)) {
                violations.add(new Violation(ValidationRule.DEFECT_NOT_DISCLOSED, "The condition disclosure must mention the "
                        + type + (location.isEmpty() ? "" : " on the " + location) + "."));
            }
        }
        if (facts.accidentsKnown() && facts.accidentsReported() > 0 && !disclosure.contains("accident")) {
            violations.add(new Violation(ValidationRule.DEFECT_NOT_DISCLOSED,
                    "The condition disclosure must mention the reported accident history."));
        }
    }

    private void checkBannedClaims(GeneratedListing listing, VehicleFacts facts, List<Violation> violations) {
        String text = lower(String.join(" ", listing.title(), listing.description(),
                String.join(" ", listing.highlights()), listing.conditionDisclosure(),
                String.join(" ", listing.seoKeywords())));
        for (String phrase : bannedPhrases) {
            if (containsPhrase(text, phrase)) {
                violations.add(new Violation(ValidationRule.BANNED_CLAIM, "Remove the claim \"" + phrase + "\"."));
            }
        }
        boolean confirmedClean = facts.accidentsKnown() && facts.accidentsReported() == 0;
        if (!confirmedClean) {
            for (String phrase : ACCIDENT_FREE_PHRASES) {
                if (containsPhrase(text, phrase)) {
                    violations.add(new Violation(ValidationRule.BANNED_CLAIM, "Remove the claim \"" + phrase
                            + "\": the vehicle history does not support it."));
                }
            }
        }
    }

    private static boolean containsPhrase(String text, String phrase) {
        return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(text).find();
    }

    private static void checkFacts(GeneratedListing listing, VehicleFacts facts, List<Violation> violations) {
        String title = lower(listing.title());
        String name = facts.year() + " " + lower(facts.make()) + " " + lower(facts.model());
        if (!title.contains(name)) {
            violations.add(new Violation(ValidationRule.FACT_MISMATCH, "The title must include \"" + facts.year() + " "
                    + facts.make() + " " + facts.model() + "\"."));
        }
        String copy = listing.title() + " " + listing.description();
        Matcher wrongYear = Pattern.compile("\\b((?:19|20)\\d{2})\\s+" + Pattern.quote(facts.make()),
                Pattern.CASE_INSENSITIVE).matcher(copy);
        while (wrongYear.find()) {
            if (Integer.parseInt(wrongYear.group(1)) != facts.year()) {
                violations.add(new Violation(ValidationRule.FACT_MISMATCH, "The text says " + wrongYear.group(1)
                        + " but the vehicle is a " + facts.year() + "."));
            }
        }
        checkOdometer(listing, facts, violations);
        if (listing.price().compareTo(facts.price()) != 0) {
            violations.add(new Violation(ValidationRule.FACT_MISMATCH, "The listing price " + listing.price()
                    + " must match the active price " + facts.price() + "."));
        }
        checkPriceInText(copy, facts.price(), violations);
    }

    private static void checkOdometer(GeneratedListing listing, VehicleFacts facts, List<Violation> violations) {
        Matcher km = KILOMETRES.matcher(listing.title() + " " + listing.description() + " "
                + String.join(" ", listing.highlights()));
        boolean mentioned = false;
        while (km.find()) {
            mentioned = true;
            int value = Integer.parseInt(km.group(1).replace(",", "")) * (km.group(2).isEmpty() ? 1 : 1000);
            if (value != facts.odometerKm()) {
                violations.add(new Violation(ValidationRule.FACT_MISMATCH, "The text says " + km.group()
                        + " but the odometer reads " + facts.odometerKm() + " km."));
            }
        }
        if (!mentioned) {
            violations.add(new Violation(ValidationRule.FACT_MISMATCH,
                    "The text must state the odometer reading of " + facts.odometerKm() + " km."));
        }
    }

    private static void checkPriceInText(String copy, Money price, List<Violation> violations) {
        Matcher dollars = DOLLARS.matcher(copy);
        while (dollars.find()) {
            double amount = Double.parseDouble(dollars.group(1).replace(",", ""));
            if (amount >= price.toDouble() * PRICE_LIKE_SHARE && amount != price.toDouble()) {
                violations.add(new Violation(ValidationRule.FACT_MISMATCH, "The text says " + dollars.group()
                        + " but the price is " + price + "."));
            }
        }
    }

    private void checkLength(GeneratedListing listing, List<Violation> violations) {
        int length = listing.description().length();
        if (length < minLength || length > maxLength) {
            violations.add(new Violation(ValidationRule.LENGTH_OUT_OF_BOUNDS, "The description has " + length
                    + " characters; it must have between " + minLength + " and " + maxLength + "."));
        }
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
