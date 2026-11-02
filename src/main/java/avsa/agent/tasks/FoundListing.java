package avsa.agent.tasks;

/** A listing as the model reported it from a web search. Any value may be null when the listing did not show it. */
public record FoundListing(String source, String url, Integer year, String make, String model, String trim,
                           Integer odometerKm, Integer askingPrice, String city, String province, String accidents,
                           String description) {
}
