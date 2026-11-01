package avsa.domain;

/** The marketplaces a listing can be exported to. */
public enum Platform {
    AUTOTRADER("AutoTrader"),
    KIJIJI("Kijiji"),
    FACEBOOK_MARKETPLACE("Facebook Marketplace");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
