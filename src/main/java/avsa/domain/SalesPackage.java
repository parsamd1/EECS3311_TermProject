package avsa.domain;

import java.util.List;

/** What a completed guided workflow produced (F12); parts the plan did not include are null or empty. */
public record SalesPackage(String summary, PriceRecommendation price, DepreciationForecast forecast,
                           PositioningReport positioning, GeneratedListing listing, List<ExportedListing> exports) {

    public SalesPackage {
        exports = exports == null ? List.of() : List.copyOf(exports);
    }
}
