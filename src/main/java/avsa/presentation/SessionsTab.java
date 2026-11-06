package avsa.presentation;

import avsa.application.SessionService;
import avsa.domain.MarketSnapshot;
import avsa.domain.PriceRecommendation;
import avsa.domain.SessionSummary;
import avsa.domain.SnapshotComparison;
import java.io.File;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/** F10: saved vehicles, snapshot comparison, a market price chart, and backups. Saving itself is automatic. */
final class SessionsTab extends FeatureTab {

    private final TableView<SessionSummary> sessions = Ui.table(180, List.of(
            Ui.col("Vehicle", SessionSummary::name, 260),
            Ui.col("Last changed", s -> Ui.when(s.lastModified()), 160),
            Ui.col("Status", this::status, 120)));
    private final TableView<MarketSnapshot> snapshots = Ui.table(150, List.of(
            Ui.col("Searched", s -> Ui.when(s.takenAt()), 150),
            Ui.col("Source", s -> s.sourceName() + (s.fromCache() ? " (saved result)" : ""), 180),
            Ui.col("Listings", s -> String.valueOf(s.stats().count()), 70),
            Ui.col("Median", s -> Ui.cad(s.stats().median()), 90),
            Ui.col("Range", s -> Ui.cad(s.stats().min()) + " - " + Ui.cad(s.stats().max()), 160)));
    private final VBox comparison = new VBox(4);
    private final VBox problems = new VBox(4);
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());

    SessionsTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Sessions";
    }

    @Override
    Node content() {
        Button open = new Button("Open");
        open.setOnAction(e -> withSelected(s -> ctx.run("Opening " + s.name() + "...", () -> service().open(s.id()),
                x -> { })));
        Button rename = new Button("Rename...");
        rename.setOnAction(e -> withSelected(s -> {
            TextInputDialog dialog = new TextInputDialog(s.name());
            dialog.initOwner(ctx.stage());
            dialog.setHeaderText("New name for this session");
            dialog.showAndWait().filter(n -> !n.isBlank()).ifPresent(n ->
                    ctx.run("Renaming...", () -> { service().rename(s.id(), n.strip()); return null; }, x -> { }));
        }));
        Button duplicate = new Button("Duplicate");
        duplicate.setOnAction(e -> withSelected(s -> ctx.run("Copying...", () -> service().duplicate(s.id()), x -> { })));
        Button delete = new Button("Delete...");
        delete.setOnAction(e -> withSelected(s -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + s.name() + " and everything saved with "
                    + "it? This cannot be undone.", ButtonType.YES, ButtonType.NO);
            confirm.initOwner(ctx.stage());
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                ctx.run("Deleting...", () -> { service().delete(s.id()); return null; }, x -> { });
            }
        }));
        Button backup = new Button("Back up all sessions...");
        backup.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose where to put the backup");
            File folder = chooser.showDialog(ctx.stage());
            if (folder != null) {
                ctx.run("Backing up...", () -> service().backup(folder.toPath()), path -> {
                    Alert done = new Alert(Alert.AlertType.INFORMATION, "Backed up to " + path);
                    done.initOwner(ctx.stage());
                    done.setHeaderText(null);
                    done.showAndWait();
                });
            }
        });
        for (Button b : List.of(open, rename, duplicate, delete)) {
            b.disableProperty().bind(sessions.getSelectionModel().selectedItemProperty().isNull());
        }

        snapshots.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        Button compare = new Button("Compare the two selected searches");
        compare.setOnAction(e -> compare());
        chart.setTitle("Market asking prices over time");
        chart.setPrefHeight(260);
        chart.setAnimated(false);

        return Ui.column(Ui.heading("Saved sessions"),
                Ui.muted("Every change is saved automatically (see the bottom bar). Sessions are kept in "
                        + AvsaCli.dataDir() + ", outside the project, because they can contain the VIN."),
                problems, sessions, new HBox(8, open, rename, duplicate, delete, backup),
                Ui.heading("Searches in the open session"), snapshots, compare, comparison, chart);
    }

    private SessionService service() {
        return controller().sessions();
    }

    private String status(SessionSummary s) {
        return session().filter(c -> c.id().equals(s.id())).map(c -> c.readOnly() ? "open, read-only" : "open").orElse("");
    }

    private void withSelected(java.util.function.Consumer<SessionSummary> action) {
        SessionSummary s = sessions.getSelectionModel().getSelectedItem();
        if (s != null) {
            action.accept(s);
        }
    }

    private void compare() {
        List<MarketSnapshot> chosen = List.copyOf(snapshots.getSelectionModel().getSelectedItems());
        if (chosen.size() != 2) {
            ctx.problem(new IllegalArgumentException("Select exactly two searches (hold Cmd or Ctrl to select the second)."));
            return;
        }
        MarketSnapshot first = chosen.get(0).takenAt().isBefore(chosen.get(1).takenAt()) ? chosen.get(0) : chosen.get(1);
        MarketSnapshot second = first == chosen.get(0) ? chosen.get(1) : chosen.get(0);
        SnapshotComparison c = service().compare(first.id(), second.id());
        comparison.getChildren().setAll(Ui.card("From " + Ui.when(first.takenAt()) + " to " + Ui.when(second.takenAt()),
                Ui.text("Median asking price " + Ui.cad(first.stats().median()) + " -> "
                        + Ui.cad(second.stats().median()) + "  (change " + Ui.cad(c.medianChange()) + ")"),
                Ui.text("Listings " + first.stats().count() + " -> " + second.stats().count() + ": "
                        + c.addedListings().size() + " new, " + c.removedListings().size() + " no longer listed")));
    }

    @Override
    void refresh() {
        problems.getChildren().clear();
        try {
            sessions.getItems().setAll(service().list());
        } catch (RuntimeException e) {
            problems.getChildren().add(Ui.error("The sessions folder could not be read: " + e.getMessage()));
        }
        service().quarantinedFiles().forEach(f -> problems.getChildren().add(Ui.warning("A damaged session file was "
                + "moved to the quarantine folder: " + f)));
        if (service().hasUnsavedChanges()) {
            problems.getChildren().add(Ui.error("Changes could not be written to disk; they are kept and saved as soon "
                    + "as the folder is writable."));
        }
        List<MarketSnapshot> list = session().map(s -> s.snapshots()).orElse(List.of());
        snapshots.getItems().setAll(list);
        comparison.getChildren().clear();
        chart.getData().clear();
        if (list.isEmpty()) {
            return;
        }
        XYChart.Series<String, Number> median = new XYChart.Series<>();
        median.setName("Median of comparables");
        XYChart.Series<String, Number> lowest = new XYChart.Series<>();
        lowest.setName("Lowest");
        XYChart.Series<String, Number> highest = new XYChart.Series<>();
        highest.setName("Highest");
        for (int i = 0; i < list.size(); i++) {
            MarketSnapshot s = list.get(i);
            String x = "#" + (i + 1) + " " + Ui.when(s.takenAt());
            median.getData().add(new XYChart.Data<>(x, s.stats().median().toDouble()));
            lowest.getData().add(new XYChart.Data<>(x, s.stats().min().toDouble()));
            highest.getData().add(new XYChart.Data<>(x, s.stats().max().toDouble()));
        }
        chart.getData().addAll(List.of(lowest, median, highest));
        List<PriceRecommendation> prices = session().map(s -> s.priceHistory()).orElse(List.of());
        if (!prices.isEmpty()) {
            comparison.getChildren().add(Ui.muted("Accepted prices: " + String.join(", ", prices.stream()
                    .map(p -> Ui.cad(p.askingPrice()) + " (" + Ui.when(p.createdAt()) + ")").toList())));
        }
    }
}
