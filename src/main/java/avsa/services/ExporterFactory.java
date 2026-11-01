package avsa.services;

import avsa.domain.Platform;
import avsa.domain.PlatformProfile;
import avsa.infrastructure.PlatformProfileLoader;
import java.time.Clock;

/** Factory Method: creates the exporter for a platform, configured with that platform's rules (F08). */
public final class ExporterFactory {

    private final PlatformProfileLoader loader;
    private final Clock clock;

    public ExporterFactory(PlatformProfileLoader loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    public ListingExporter createExporter(Platform platform) {
        PlatformProfile profile = loader.load(platform);
        return switch (platform) {
            case AUTOTRADER -> new AutoTraderExporter(profile, clock);
            case KIJIJI -> new KijijiExporter(profile, clock);
            case FACEBOOK_MARKETPLACE -> new FacebookMarketplaceExporter(profile, clock);
        };
    }
}
