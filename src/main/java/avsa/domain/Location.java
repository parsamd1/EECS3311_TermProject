package avsa.domain;

/** A place in Canada with coordinates, used to measure distance to comparable listings. */
public record Location(String city, String province, double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Great-circle (haversine) distance in kilometres. */
    public double distanceKmTo(Location other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
