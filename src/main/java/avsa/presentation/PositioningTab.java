package avsa.presentation;

import avsa.domain.Claim;
import avsa.domain.ClaimKind;
import avsa.domain.ComparableListing;
import avsa.domain.MarketSnapshot;
import avsa.domain.PercentileMetric;
import avsa.domain.PositioningReport;
import avsa.domain.VehicleProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * F05: how the vehicle compares with the top comparables. The metrics are computed; the AI's claims
 * are kept only when the cited comparables support them, and each claim shows what it cites.
 */
final class PositioningTab extends FeatureTab {

    private static final Map<ClaimKind, String> HEADINGS = Map.of(ClaimKind.ADVANTAGE, "Advantages",
            ClaimKind.WEAKNESS, "Weaknesses", ClaimKind.REMEDY, "How to address them",
            ClaimKind.TALKING_POINT, "Talking points");

    /** One row of the side-by-side table: the seller's vehicle or a comparable. */
    private record Row(String label, int year, String trim, int km, String price, String distance, String source) {
    }

    private final TableView<Row> sideBySide = Ui.table(190, List.of(
            Ui.col("", Row::label, 110),
            Ui.col("Year", r -> String.valueOf(r.year()), 60),
            Ui.col("Trim", Row::trim, 140),
            Ui.col("Odometer", r -> Ui.km(r.km()), 100),
            Ui.col("Price", Row::price, 90),
            Ui.col("Distance", Row::distance, 80),
            Ui.col("Source", Row::source, 100)));
    private final TableView<PercentileMetric> metrics = Ui.table(130, List.of(
            Ui.col("Metric", m -> m.name(), 100),
            Ui.col("Yours", PositioningTab::value, 100),
            Ui.col("Percentile", m -> String.format(Locale.ROOT, "%.0f%% of comparables are below yours",
                    m.percentile()), 260),
            Ui.col("Better when", m -> m.betterWhenLower() ? "lower" : "higher", 100)));
    private final VBox status = new VBox(4);
    private final FlowPane cards = new FlowPane(12, 12);

    PositioningTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Positioning";
    }

    @Override
    Node content() {
        Button analyze = new Button("Analyze positioning");
        analyze.setDefaultButton(true);
        analyze.setOnAction(e -> ctx.run("Comparing your vehicle with the top comparables...",
                () -> controller().analyzePositioning(), r -> { }));
        cards.setPrefWrapLength(1100);
        return Ui.column(Ui.heading("Competitive positioning"),
                Ui.muted("Uses the active price and the top five comparables that are not excluded. Owner counts are "
                        + "not compared because listings do not state them."),
                analyze, status, sideBySide, metrics, cards);
    }

    @Override
    void refresh() {
        status.getChildren().clear();
        cards.getChildren().clear();
        List<ComparableListing> top = session().flatMap(s -> s.latestSnapshot()).map(MarketSnapshot::rankedListings)
                .orElse(List.of()).stream().filter(c -> !c.excluded()).limit(5).toList();
        List<Row> rows = new ArrayList<>();
        session().ifPresent(s -> {
            VehicleProfile p = s.activeProfile();
            rows.add(new Row("Your vehicle", p.year(), p.trim(), p.odometerKm(),
                    s.activePrice().map(a -> Ui.cad(a.askingPrice())).orElse("-"), "", ""));
        });
        for (int i = 0; i < top.size(); i++) {
            ComparableListing c = top.get(i);
            rows.add(new Row("C" + (i + 1), c.year(), c.trim(), c.odometerKm(), Ui.cad(c.askingPrice()),
                    String.format(Locale.ROOT, "%.0f km", c.distanceKm()), c.source()));
        }
        sideBySide.getItems().setAll(rows);
        Optional<PositioningReport> report = session().flatMap(s -> s.positioning());
        metrics.getItems().setAll(report.map(PositioningReport::metrics).orElse(List.of()));
        if (report.isEmpty()) {
            status.getChildren().add(Ui.muted(session().flatMap(s -> s.activePrice()).isPresent()
                    ? "Not analyzed yet." : "Find comparables and accept a price first."));
            return;
        }
        PositioningReport r = report.get();
        if (r.metricsOnly()) {
            status.getChildren().add(Ui.source(false, "Metrics only (AI unavailable)"));
        }
        // The notices already say how many AI claims were removed and why.
        r.notices().forEach(n -> status.getChildren().add(Ui.styled(Ui.text(n), "warning")));
        for (ClaimKind kind : ClaimKind.values()) {
            List<Claim> claims = r.claims(kind);
            if (!claims.isEmpty()) {
                List<Node> lines = new ArrayList<>();
                lines.add(Ui.source(true, ""));
                for (Claim c : claims) {
                    lines.add(Ui.text("• " + c.text()));
                    lines.add(Ui.muted("   Based on: " + String.join(", ", c.evidenceRefs().stream()
                            .map(ref -> cite(ref, top)).toList())));
                }
                cards.getChildren().add(card(HEADINGS.get(kind), lines));
            }
        }
        if (!r.priceJustification().isBlank()) {
            cards.getChildren().add(card("Price justification", List.of(Ui.text(r.priceJustification()))));
        }
    }

    private static VBox card(String title, List<Node> lines) {
        VBox card = Ui.card(title, lines.toArray(Node[]::new));
        card.setPrefWidth(530);
        return card;
    }

    /** A cited listing id becomes its C label; a metric name stays as it is. */
    private static String cite(String ref, List<ComparableListing> top) {
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).listingId().equals(ref)) {
                return "C" + (i + 1);
            }
        }
        return ref.length() > 20 ? "a comparable no longer in the top five" : ref;
    }

    private static String value(PercentileMetric m) {
        return switch (m.name()) {
            case "year" -> String.valueOf((int) m.value());
            case "price" -> "$" + String.format(Locale.ROOT, "%,.0f", m.value());
            case "odometer" -> String.format(Locale.ROOT, "%,.0f km", m.value());
            default -> String.format(Locale.ROOT, "%,.0f", m.value());
        };
    }
}
