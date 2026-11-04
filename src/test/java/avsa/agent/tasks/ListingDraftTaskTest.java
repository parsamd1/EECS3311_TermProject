package avsa.agent.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.MockLLMClient;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.BodyStyle;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingOptions;
import avsa.domain.Location;
import avsa.domain.MaintenanceRecord;
import avsa.domain.Money;
import avsa.domain.Severity;
import avsa.domain.Transmission;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.JsonMapper;
import avsa.services.ListingValidator;
import avsa.services.TemplateListingWriter;
import avsa.tools.ListingTool;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingDraftTaskTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private static final VehicleFacts FACTS = VehicleFacts.from(VehicleProfile.builder().make("Mazda").model("CX-5")
            .year(2022).trim("GS").bodyStyle(BodyStyle.SUV).odometerKm(49_000).transmission(Transmission.AUTOMATIC)
            .fuelType(FuelType.GASOLINE).location(new Location("Toronto", "ON", 43.65, -79.38))
            .features(List.of("Winter tires"))
            .maintenanceRecords(List.of(new MaintenanceRecord(LocalDate.of(2024, 12, 13), 30_000, "Oil change")))
            .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door", Severity.MINOR,
                    "", null)))).build(), null, Money.cad(29_500));

    private final MockLLMClient llm = new MockLLMClient();
    private final ListingTool tool = new ListingTool(new AiTaskRunner(new PromptComposer(), llm,
            new ResponseParser(new JsonMapper()), new AgentTrace(CLOCK)), new TemplateListingWriter(),
            new ListingValidator(CLOCK));

    private static String draft(String disclosure, String extra) {
        return "{\"title\": \"2022 Mazda CX-5 GS - 49,000 km\", \"description\": \"Well kept 2022 Mazda CX-5 GS with "
                + "49,000 km, automatic, gasoline, located in Toronto and ready for a new owner." + extra + "\", "
                + "\"highlights\": [\"Winter tires\"], \"conditionDisclosure\": \"" + disclosure + "\", "
                + "\"seoKeywords\": [\"Mazda CX-5\"]}";
    }

    private GeneratedListing generate() {
        ToolResult r = tool.execute(ToolArgs.of("facts", FACTS, "options", ListingOptions.defaults()));
        return r.valueAs(GeneratedListing.class);
    }

    @Test
    void aValidAiDraftIsUsedWithTheRecordedMaintenance() {
        llm.thenRespond(draft("Minor scratch on the rear door.", ""));
        GeneratedListing listing = generate();
        assertFalse(listing.templateFallback());
        assertTrue(listing.isExportable());
        assertEquals(List.of("2024-12-13: Oil change at 30,000 km"), listing.maintenanceDetails());
        assertEquals(Money.cad(29_500), listing.price());
    }

    @Test
    void aDraftThatHidesADefectIsSentBackWithTheViolation() {
        llm.thenRespond(draft("Great shape overall.", "")).thenRespond(draft("Minor scratch on the rear door.", ""));
        assertTrue(generate().isExportable());
        assertTrue(llm.received().get(1).user().contains("must mention the scratch on the rear door"));
    }

    @Test
    void afterThreeFailingDraftsTheBestOneIsKeptWithItsWarnings() {
        llm.thenRespond(draft("Great shape.", " Flawless! No issues."))
                .thenRespond(draft("Great shape.", " Flawless!"))
                .thenRespond(draft("Great shape.", " Flawless! No issues. Like new."));
        ToolResult r = tool.execute(ToolArgs.of("facts", FACTS));
        GeneratedListing best = r.valueAs(GeneratedListing.class);
        assertEquals(3, llm.received().size());
        assertFalse(best.templateFallback());
        assertFalse(best.isExportable());
        assertEquals(2, best.report().orElseThrow().violations().size(), "the second draft had the fewest problems");
        assertTrue(r.notes().get(0).contains("closest"));
    }

    @Test
    void whenTheAiIsUnavailableATemplateListingIsWrittenAndLabelled() {
        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE);
        ToolResult r = tool.execute(ToolArgs.of("facts", FACTS));
        GeneratedListing listing = r.valueAs(GeneratedListing.class);
        assertTrue(listing.templateFallback());
        assertTrue(listing.isExportable());
        assertTrue(r.notes().get(0).contains("template"));
    }

    @Test
    void thePromptCarriesTheFactsAndWhatMustBeDisclosedButNoVin() {
        llm.thenRespond(draft("Minor scratch on the rear door.", ""));
        generate();
        String prompt = llm.received().get(0).user();
        assertTrue(prompt.contains("Odometer: 49,000 km"));
        assertTrue(prompt.contains("Must disclose:\n- minor scratch on the rear door"));
        assertTrue(prompt.contains("Accident history: not confirmed"));
        assertFalse(prompt.toUpperCase().contains("VIN"));
    }
}
