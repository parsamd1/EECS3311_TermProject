package avsa.services;

import avsa.domain.VehicleHistory;

/**
 * What the seller asked for in a comparable search.
 *
 * @param yearWindow  model years either side of the seller's that count as similar
 * @param requireTrim only the seller's trim and its variants, until a search has to be widened
 * @param history     the seller's vehicle history; may be null
 */
public record SearchRequest(int radiusKm, int maxResults, int yearWindow, boolean requireTrim, VehicleHistory history) {

    public static final int DEFAULT_RADIUS_KM = 100;
    public static final int DEFAULT_MAX_RESULTS = 10;
    public static final int DEFAULT_YEAR_WINDOW = 2;

    public SearchRequest {
        if (radiusKm <= 0 || maxResults <= 0 || yearWindow < 0) {
            throw new IllegalArgumentException("Radius and maximum results must be positive.");
        }
    }

    public static SearchRequest defaults() {
        return new SearchRequest(DEFAULT_RADIUS_KM, DEFAULT_MAX_RESULTS, DEFAULT_YEAR_WINDOW, true, null);
    }

    public SearchRequest withRadius(int km) {
        return new SearchRequest(km, maxResults, yearWindow, requireTrim, history);
    }

    public SearchRequest withYearWindow(int years) {
        return new SearchRequest(radiusKm, maxResults, years, requireTrim, history);
    }

    public SearchRequest withoutTrimRequirement() {
        return new SearchRequest(radiusKm, maxResults, yearWindow, false, history);
    }

    public SearchRequest withHistory(VehicleHistory vehicleHistory) {
        return new SearchRequest(radiusKm, maxResults, yearWindow, requireTrim, vehicleHistory);
    }
}
