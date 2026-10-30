package avsa.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.AccidentRecord;
import avsa.domain.AdjustmentKind;
import avsa.domain.BodyStyle;
import avsa.domain.BuyerType;
import avsa.domain.ComparableListing;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.DepreciationForecast;
import avsa.domain.FuelType;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.Location;
import avsa.domain.MaintenanceRecord;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.OfferVerdict;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Segment;
import avsa.domain.Session;
import avsa.domain.Severity;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.JsonSessionRepository;
import avsa.infrastructure.SchemaMigrator;
import avsa.infrastructure.SessionLoadException;
import avsa.services.DepreciationCalculator;
import avsa.services.DepreciationRateTable;
import avsa.services.ForecastInput;
import avsa.services.OfferEvaluator;
import avsa.services.PricingEngine;
import avsa.services.PricingInput;
import avsa.services.QuickSaleStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private JsonSessionRepository repository;
    private SessionService service;

    @BeforeEach
    void setUp() {
        repository = new JsonSessionRepository(dir, new JsonMapper(), new SchemaMigrator());
        service = new SessionService(repository, CLOCK);
    }

    private static VehicleProfile cx5() {
        return VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.65, -79.38)).features(List.of("Winter tires"))
                .maintenanceRecords(List.of(new MaintenanceRecord(LocalDate.of(2024, 12, 13), 30_000, "Service")))
                .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door",
                        Severity.MINOR, "About 25 cm hairline", null))))
                .build();
    }

    private static PriceRecommendation price(VehicleProfile profile) {
        List<ComparableListing> comps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            comps.add(new ComparableListing("c" + i, "Kijiji", 2022, "GS", 50_000, Money.cad(30_000), 10, "", ""));
        }
        return new PricingEngine(CLOCK).recommend(
                new PricingInput(profile, comps, PricingParameters.defaults()), new QuickSaleStrategy());
    }

    @Test
    void aFullSessionSurvivesASaveAndLoad() {
        VehicleProfile profile = cx5();
        Session session = service.create(profile);
        session.setHistory(new VehicleHistory(null, 2, 1, LienStatus.NONE, List.of("Quebec", "Ontario"),
                HistorySourceType.MANUAL, true, List.of(new AccidentRecord(null, Severity.MINOR, "Bumper")), List.of()));
        PriceRecommendation rec = price(profile);
        rec.setExplanation("Because of the market.", true);
        session.setActivePrice(rec);
        DepreciationForecast forecast = new DepreciationCalculator(DepreciationRateTable.builtIn()).forecast(
                new ForecastInput(Money.cad(28_500), 2022, Segment.SUV, LocalDate.of(2026, 10, 30), Money.cad(150),
                        Money.cad(24_500)));
        session.setForecast(forecast);
        OfferEvaluation evaluation = new OfferEvaluator().evaluate(
                new Offer(Money.cad(23_500), BuyerType.DEALER, "Dealer said high km", CLOCK.instant()), rec, forecast);
        session.addOffer(evaluation);
        service.saveCurrent();

        Session loaded = new JsonSessionRepository(dir, new JsonMapper(), new SchemaMigrator()).load(session.id());

        VehicleProfile p = loaded.activeProfile();
        assertEquals(profile.id(), p.id());
        assertEquals("CX-5", p.model());
        assertEquals(49_000, p.odometerKm());
        assertEquals(List.of("Winter tires"), p.features());
        assertEquals(Segment.SUV, p.segment());
        assertEquals(1, p.maintenanceRecords().size());
        assertEquals(1, p.condition().severityScore());
        assertEquals(new Location("Toronto", "ON", 43.65, -79.38), p.location());

        assertEquals(List.of("Quebec", "Ontario"), loaded.history().orElseThrow().registrationProvinces());
        assertEquals(1, loaded.history().orElseThrow().accidents().size());

        PriceRecommendation lp = loaded.activePrice().orElseThrow();
        assertEquals(rec.id(), lp.id());
        assertEquals(rec.wholesale(), lp.wholesale());
        assertEquals(rec.retail(), lp.retail());
        assertEquals(rec.askingPrice(), lp.askingPrice());
        assertEquals(rec.strategy(), lp.strategy());
        assertEquals(rec.parameters(), lp.parameters());
        assertEquals(rec.adjustments().stream().filter(a -> a.kind() == AdjustmentKind.CONDITION).count(),
                lp.adjustments().stream().filter(a -> a.kind() == AdjustmentKind.CONDITION).count());
        assertEquals("Because of the market.", lp.explanation().orElseThrow());
        assertTrue(lp.templateExplanation());

        DepreciationForecast lf = loaded.forecast().orElseThrow();
        assertEquals(forecast.points(), lf.points());
        assertEquals(forecast.breakEvenMonth(), lf.breakEvenMonth());

        OfferEvaluation le = loaded.offerLog().get(0);
        assertEquals(OfferVerdict.LOW_BALL, le.verdict());
        assertEquals("Dealer said high km", le.offer().notes());
        assertEquals(evaluation.counterAmount(), le.counterAmount());
    }

    @Test
    void newHistoryMakesTheActivePriceStaleAndItStaysStaleAfterReload() {
        VehicleProfile profile = cx5();
        Session session = service.create(profile);
        session.setActivePrice(price(profile));
        assertFalse(session.activePrice().orElseThrow().stale());

        session.setHistory(new VehicleHistory(null, 1, 0, LienStatus.NONE, List.of(), HistorySourceType.MANUAL, true,
                List.of(), List.of()));
        assertTrue(session.activePrice().orElseThrow().stale());
        service.saveCurrent();
        assertTrue(repository.load(session.id()).activePrice().orElseThrow().stale());
    }

    @Test
    void listsMostRecentFirstAndSupportsRenameDuplicateDelete() {
        Session a = service.create(cx5());
        Session copy = service.duplicate(a.id());
        assertNotEquals(a.id(), copy.id());
        assertEquals("2022 Mazda CX-5 (copy)", copy.name());
        assertEquals(2, service.list().size());

        service.rename(a.id(), "My CX-5");
        assertEquals("My CX-5", service.current().name());

        service.delete(a.id());
        assertFalse(service.hasCurrent());
        assertEquals(1, service.list().size());
        assertThrows(SessionLoadException.class, () -> repository.load(a.id()));
    }

    @Test
    void featuresWithoutASessionAreDirectedToCreateAProfile() {
        assertThrows(IllegalStateException.class, service::current);
    }

    @Test
    void findsAnExistingSessionForTheSameVehicle() {
        Session saved = service.create(cx5());
        assertEquals(saved.id(), service.findMatching(cx5()).orElseThrow().id());
        VehicleProfile other = VehicleProfile.builder().make("Honda").model("Civic").year(2020).build();
        assertTrue(service.findMatching(other).isEmpty());
    }

    @Test
    void corruptFileIsQuarantinedAndOtherSessionsStillLoad() throws IOException {
        Session good = service.create(cx5());
        UUID badId = UUID.randomUUID();
        Files.writeString(dir.resolve(badId + ".json"), "{ this is not json");

        assertEquals(List.of(good.id()), service.list().stream().map(s -> s.id()).toList());
        assertEquals(List.of(badId + ".json"), service.quarantinedFiles());
        assertFalse(Files.exists(dir.resolve(badId + ".json")));
        assertTrue(Files.exists(dir.resolve("quarantine").resolve(badId + ".json")));
    }

    @Test
    void wellFormedFileWithInvalidContentIsAlsoQuarantined() throws IOException {
        UUID id = UUID.randomUUID();
        Files.writeString(dir.resolve(id + ".json"), "{\"schemaVersion\": 1, \"name\": \"no profile\"}");
        assertThrows(SessionLoadException.class, () -> repository.load(id));
        assertEquals(1, repository.quarantined().size());
    }

    @Test
    void olderFileIsMigratedAndNewerFileOpensReadOnly() throws IOException {
        Session session = service.create(cx5());
        Path file = dir.resolve(session.id() + ".json");
        String json = Files.readString(file);

        Files.writeString(file, json.replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 0"));
        Session migrated = repository.load(session.id());
        assertEquals(Session.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertFalse(migrated.readOnly());

        Files.writeString(file, json.replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 99"));
        Session newer = service.open(session.id());
        assertTrue(newer.readOnly());
        Instant before = newer.lastModified();
        service.saveCurrent();
        assertTrue(Files.readString(file).contains("\"schemaVersion\" : 99"), "read-only sessions are not overwritten");
        assertEquals(before, newer.lastModified());
    }

    @Test
    void diskErrorKeepsDataInMemoryAndRetriesOnTheNextSave() throws IOException {
        Path blocked = dir.resolve("blocked");
        Files.writeString(blocked, "a file where the data folder should be");
        JsonSessionRepository repo = new JsonSessionRepository(blocked, new JsonMapper(), new SchemaMigrator());
        SessionService svc = new SessionService(repo, CLOCK);

        Session session = svc.create(cx5());
        assertTrue(svc.hasUnsavedChanges());
        assertEquals(session.id(), repo.load(session.id()).id(), "still available from memory");

        Files.delete(blocked);
        svc.saveCurrent();
        assertFalse(svc.hasUnsavedChanges());
        assertTrue(Files.exists(blocked.resolve(session.id() + ".json")));
    }
}
