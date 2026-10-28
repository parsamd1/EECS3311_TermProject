package avsa.domain;

import java.util.Objects;

/**
 * A similar vehicle for sale (F02). Pricing (F03) uses its asking price,
 * odometer and similarity score. The seller can pin or exclude a listing.
 */
public final class ComparableListing {

    private final String listingId;
    private final String source;
    private final int year;
    private final String trim;
    private final int odometerKm;
    private final Money askingPrice;
    private final double distanceKm;
    private final String description;
    private final String url;
    private double score = 1.0;
    private boolean pinned;
    private boolean excluded;

    public ComparableListing(String listingId, String source, int year, String trim, int odometerKm,
                             Money askingPrice, double distanceKm, String description, String url) {
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.source = source == null ? "" : source;
        this.year = year;
        this.trim = trim == null ? "" : trim;
        this.odometerKm = odometerKm;
        this.askingPrice = Objects.requireNonNull(askingPrice, "askingPrice");
        this.distanceKm = distanceKm;
        this.description = description == null ? "" : description;
        this.url = url == null ? "" : url;
    }

    public String listingId() { return listingId; }
    public String source() { return source; }
    public int year() { return year; }
    public String trim() { return trim; }
    public int odometerKm() { return odometerKm; }
    public Money askingPrice() { return askingPrice; }
    public double distanceKm() { return distanceKm; }
    public String description() { return description; }
    public String url() { return url; }

    /** Similarity score in [0, 1]; also the weight in the pricing median. */
    public double score() { return score; }
    public boolean pinned() { return pinned; }
    public boolean excluded() { return excluded; }

    public void setScore(double score) {
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be in [0, 1]: " + score);
        }
        this.score = score;
    }

    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }
}
