package avsa.presentation;

import avsa.domain.ComparableListing;
import avsa.domain.ListingFlag;
import avsa.domain.MarketSnapshot;
import avsa.domain.ScoreBreakdown;
import avsa.infrastructure.market.MarketSourceType;
import avsa.services.SearchRequest;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** F02: find comparables, show them ranked with their score breakdown, and let the seller exclude or pin them. */
final class MarketTab extends FeatureTab {

    private final Spinner<Integer> radius = new Spinner<>(10, 1000, SearchRequest.DEFAULT_RADIUS_KM, 10);
    private final Spinner<Integer> maxResults = new Spinner<>(3, 30, SearchRequest.DEFAULT_MAX_RESULTS);
    private final ComboBox<MarketSourceType> source = new ComboBox<>();
    private final Label csvLabel = Ui.muted("No file chosen");
    private final Button chooseCsv = new Button("Choose CSV...");
    private final Label liveNote = Ui.muted("");
    private final VBox summary = new VBox(4);
    private final TableView<ComparableListing> table = Ui.table(300, List.of(
            Ui.rank(34),
            Ui.col("Score", l -> String.format(Locale.ROOT, "%.2f", l.score()), 60),
            Ui.col("Year", l -> String.valueOf(l.year()), 55),
            Ui.col("Trim", ComparableListing::trim, 130),
            Ui.col("Odometer", l -> Ui.km(l.odometerKm()), 95),
            Ui.col("Price", l -> Ui.cad(l.askingPrice()), 85),
            Ui.col("Distance", l -> String.format(Locale.ROOT, "%s%.0f km", l.locationApproximate() ? "~" : "",
                    l.distanceKm()), 80),
            Ui.col("Source", ComparableListing::source, 100),
            Ui.col("Status and flags", MarketTab::status, 220)));
    private final VBox detail = new VBox(6);
    private final VBox sources = new VBox(2);
    private final Button exclude = new Button("Exclude");
    private final Button pin = new Button("Pin");
    private Path csvFile;

    MarketTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Market";
    }

    @Override
    Node content() {
        source.getItems().setAll(MarketSourceType.values());
        source.setConverter(new StringConverter<>() {
            @Override
            public String toString(MarketSourceType t) {
                return t == null ? "" : switch (t) {
                    case SEEDED -> "Seeded dataset (real Ontario listings, Oct 2026)";
                    case CSV -> "Imported CSV file";
                    case LIVE -> "Live search: AutoTrader.ca, Kijiji, CarGurus";
                };
            }

            @Override
            public MarketSourceType fromString(String s) {
                return null;
            }
        });
        source.setValue(MarketSourceType.SEEDED);
        source.valueProperty().addListener((o, a, b) -> updateSourceControls());
        chooseCsv.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File file = chooser.showOpenDialog(ctx.stage());
            if (file != null) {
                csvFile = file.toPath();
                csvLabel.setText(file.getName());
            }
        });
        radius.setEditable(true);
        maxResults.setEditable(true);

        GridPane form = Ui.form();
        Ui.row(form, "Data source", source);
        Ui.row(form, "CSV file", new HBox(8, chooseCsv, csvLabel));
        Ui.row(form, "Search radius (km)", radius);
        Ui.row(form, "Maximum results", maxResults);
        Button find = new Button("Find comparables");
        find.setDefaultButton(true);
        find.setOnAction(e -> find());

        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showDetail(b));
        exclude.setOnAction(e -> toggleExcluded());
        pin.setOnAction(e -> togglePinned());
        updateSourceControls();
        return Ui.column(Ui.heading("Comparable listings"),
                Ui.muted("Listings like yours nearby, scored on year, odometer, trim, distance, condition and history. "
                        + "The AI only flags problems in listing text; the code applies a fixed penalty per flag."),
                Ui.card(null, form, liveNote, find), summary, table,
                new HBox(8, exclude, pin, Ui.muted("Excluded listings are left out of pricing and positioning; "
                        + "pinned ones are always used and ranked first.")),
                detail, sources);
    }

    private void updateSourceControls() {
        boolean csv = source.getValue() == MarketSourceType.CSV;
        chooseCsv.setDisable(!csv);
        csvLabel.setDisable(!csv);
        boolean live = source.getValue() == MarketSourceType.LIVE;
        liveNote.setText(!live ? "" : controller().liveSearchConfigured()
                ? "Claude searches the marketplaces and reads the listing pages. It usually takes one to three minutes "
                + "and costs about 5 cents. Only listings whose page was really found are kept; if the search fails, "
                + "the last saved live result is used (with its date), then the seeded data."
                : "Live search needs an API key in .env. The seeded dataset will be used instead.");
    }

    private void find() {
        if (source.getValue() == MarketSourceType.CSV && csvFile == null) {
            ctx.problem(new IllegalArgumentException("Choose the CSV file to import first."));
            return;
        }
        SearchRequest request = new SearchRequest(radius.getValue(), maxResults.getValue(),
                SearchRequest.DEFAULT_YEAR_WINDOW, true, null);
        String message = source.getValue() == MarketSourceType.LIVE && controller().liveSearchConfigured()
                ? "Searching AutoTrader.ca, Kijiji and CarGurus for listings like yours. This usually takes one to "
                + "three minutes..."
                : "Finding and scoring comparables...";
        ctx.run(message, () -> controller().findComparables(request, source.getValue(), csvFile), s -> { });
    }

    private void toggleExcluded() {
        ComparableListing l = table.getSelectionModel().getSelectedItem();
        ctx.run("Updating the comparables...", () -> controller().excludeComparable(l.listingId(), !l.excluded()),
                s -> select(l.listingId()));
    }

    private void togglePinned() {
        ComparableListing l = table.getSelectionModel().getSelectedItem();
        ctx.run("Updating the comparables...", () -> controller().pinComparable(l.listingId(), !l.pinned()),
                s -> select(l.listingId()));
    }

    private void select(String listingId) {
        table.getItems().stream().filter(l -> l.listingId().equals(listingId)).findFirst()
                .ifPresent(l -> table.getSelectionModel().select(l));
    }

    @Override
    void refresh() {
        updateSourceControls();
        summary.getChildren().clear();
        sources.getChildren().clear();
        MarketSnapshot s = session().flatMap(x -> x.latestSnapshot()).orElse(null);
        String selected = table.getSelectionModel().getSelectedItem() == null ? null
                : table.getSelectionModel().getSelectedItem().listingId();
        table.getItems().setAll(s == null ? List.of() : s.rankedListings());
        table.refresh();
        if (selected != null) {
            select(selected);
        } else {
            showDetail(null);
        }
        if (s == null) {
            summary.getChildren().add(Ui.muted(session().isPresent() ? "No search yet." : "Save a vehicle profile first."));
            return;
        }
        Label sourceLine = Ui.text("Source: " + s.sourceName() + "   Searched " + Ui.when(s.takenAt()) + "   Radius "
                + s.radiusKm() + " km");
        summary.getChildren().add(sourceLine);
        if (s.fromCache()) {
            summary.getChildren().add(Ui.warning("The search could not run, so this is the saved result from "
                    + Ui.when(s.retrievedAt()) + "."));
        }
        if (s.lowConfidence()) {
            summary.getChildren().add(Ui.warning("Low confidence: few comparables. Try a wider radius, another search, "
                    + "or a CSV import."));
        }
        summary.getChildren().add(Ui.styled(Ui.text(String.format(Locale.ROOT,
                "%d listings used:  lowest %s   median %s   highest %s", s.stats().count(), Ui.cad(s.stats().min()),
                Ui.cad(s.stats().median()), Ui.cad(s.stats().max()))), "card-title"));
        if (s.skippedRecords() > 0) {
            summary.getChildren().add(Ui.muted(s.skippedRecords() + " malformed records were skipped."));
        }
        s.warnings().forEach(w -> summary.getChildren().add(Ui.warning(w)));
        Set<String> links = new LinkedHashSet<>();
        s.rankedListings().stream().map(ComparableListing::url).filter(u -> !u.isBlank()).forEach(links::add);
        if (!links.isEmpty()) {
            sources.getChildren().add(Ui.styled(new Label("Sources (open a listing to check it)"), "card-title"));
            links.forEach(url -> sources.getChildren().add(link(url)));
        }
    }

    private Hyperlink link(String url) {
        Hyperlink link = new Hyperlink(url);
        link.setOnAction(e -> ctx.openLink(url));
        return link;
    }

    private void showDetail(ComparableListing l) {
        detail.getChildren().clear();
        exclude.setDisable(l == null);
        pin.setDisable(l == null);
        if (l == null) {
            exclude.setText("Exclude");
            pin.setText("Pin");
            return;
        }
        exclude.setText(l.excluded() ? "Include again" : "Exclude");
        pin.setText(l.pinned() ? "Unpin" : "Pin");
        List<Node> rows = new ArrayList<>();
        rows.add(Ui.text(l.year() + " " + l.trim() + ", " + Ui.km(l.odometerKm()) + ", " + Ui.cad(l.askingPrice()) + " ("
                + l.source() + ")" + (l.locationApproximate() ? ". The listing gave no city, so the searched city was "
                + "assumed and distance scores neutral." : "")));
        l.breakdown().ifPresent(b -> rows.add(breakdown(b)));
        rows.add(Ui.text("Accident history in the listing: " + Ui.label(l.accidentStatus())
                + (l.brandedTitle() ? "   Branded title" : "")));
        if (!l.reviewed()) {
            rows.add(Ui.muted("Not reviewed by the AI (it was unavailable), so no text flags were checked."));
        }
        for (ListingFlag f : l.flags()) {
            rows.add(Ui.warning(Ui.label(f.code()) + ": " + f.reason()));
        }
        if (!l.description().isBlank()) {
            rows.add(Ui.muted(l.description()));
        }
        if (!l.url().isBlank()) {
            rows.add(link(l.url()));
        }
        detail.getChildren().add(Ui.card("Selected listing", rows.toArray(Node[]::new)));
    }

    private static Node breakdown(ScoreBreakdown b) {
        GridPane grid = Ui.form();
        String[][] parts = {{"Year", fmt(b.year())}, {"Odometer", fmt(b.odometer())}, {"Trim", fmt(b.trim())},
            {"Distance", fmt(b.distance())}, {"Condition", fmt(b.condition())}, {"History", fmt(b.history())},
            {"Flag penalty", b.flagPenalty() == 0 ? "0" : "-" + fmt(b.flagPenalty())}, {"Total", fmt(b.total())}};
        for (int i = 0; i < parts.length; i++) {
            grid.add(new Label(parts[i][0]), i, 0);
            grid.add(Ui.styled(new Label(parts[i][1]), "card-title"), i, 1);
        }
        return grid;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String status(ComparableListing l) {
        List<String> marks = new ArrayList<>();
        if (l.pinned()) {
            marks.add("PINNED");
        }
        if (l.excluded()) {
            marks.add("EXCLUDED");
        }
        if (!l.reviewed()) {
            marks.add("unreviewed");
        }
        l.flags().forEach(f -> marks.add(Ui.label(f.code())));
        return String.join(", ", marks);
    }
}
