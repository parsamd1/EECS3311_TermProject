package avsa.infrastructure.market;

import avsa.domain.ComparableListing;
import avsa.domain.ListingAccidentStatus;
import avsa.domain.Location;
import avsa.domain.Money;
import avsa.domain.VehicleProfile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turns raw records into clean listings. Records that cannot be used come back empty and are counted as skipped. */
public final class ListingNormalizer {

    private final Gazetteer gazetteer;

    public ListingNormalizer(Gazetteer gazetteer) {
        this.gazetteer = gazetteer;
    }

    public Optional<ComparableListing> normalize(RawListing raw, Location origin) {
        Optional<Integer> year = number(raw.year());
        Optional<Integer> odometer = number(raw.odometerKm());
        Optional<Integer> price = number(raw.askingPrice());
        Optional<Location> place = gazetteer.resolve(raw.city(), raw.province());
        if (year.isEmpty() || odometer.isEmpty() || price.isEmpty() || place.isEmpty()
                || year.get() < VehicleProfile.MIN_YEAR || price.get() <= 0) {
            return Optional.empty();
        }
        String url = cleanUrl(raw.url());
        // A results page shows many listings, so the address alone does not identify one.
        String identity = String.join("|", url.isEmpty() ? raw.source() : url, raw.year(), raw.trim(),
                raw.odometerKm(), raw.askingPrice(), url.isEmpty() ? raw.city() : "");
        ComparableListing listing = new ComparableListing(
                UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString(), cleanSource(raw.source()),
                year.get(), cleanTrim(raw.trim()), odometer.get(), Money.cad(price.get().longValue()),
                origin.distanceKmTo(place.get()), raw.description(), url);
        listing.setHistoryFacts(accidentStatus(raw.accidents()), isBrandedTitle(raw.titleStatus()));
        listing.setLocationApproximate(raw.locationAssumed());
        return Optional.of(listing);
    }

    /** Digits only, so "$28,900" and "51,000 km" both work; anything else, or nothing, is not a number. */
    private static Optional<Integer> number(String text) {
        if (text == null || !text.strip().matches("\\$?[\\d,]+(\\s*km)?")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(text.replaceAll("\\D", "")));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Removes tracking parameters, so the same advertisement always has the same address. */
    static String cleanUrl(String url) {
        if (url == null) {
            return "";
        }
        int query = url.indexOf('?');
        return (query < 0 ? url : url.substring(0, query)).strip();
    }

    static String cleanSource(String source) {
        return source != null && source.strip().equalsIgnoreCase("autotrader") ? "AutoTrader"
                : source == null ? "" : source.strip();
    }

    /** Drivetrain is not part of the trim name: "GX AWD" is the GX trim. */
    static String cleanTrim(String trim) {
        if (trim == null) {
            return "";
        }
        return Arrays.stream(trim.strip().split("\\s+"))
                .filter(word -> !word.toUpperCase(Locale.ROOT).matches("AWD|FWD|RWD|4WD|4X4"))
                .collect(Collectors.joining(" "));
    }

    static ListingAccidentStatus accidentStatus(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (s.contains("no accident")) {
            return ListingAccidentStatus.NONE;
        }
        if (s.contains("accident")) {
            return ListingAccidentStatus.REPORTED;
        }
        return s.contains("glass") ? ListingAccidentStatus.NONE : ListingAccidentStatus.UNKNOWN;
    }

    static boolean isBrandedTitle(String title) {
        String s = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return s.contains("salvage") || s.contains("rebuilt") || s.contains("irreparable");
    }
}
