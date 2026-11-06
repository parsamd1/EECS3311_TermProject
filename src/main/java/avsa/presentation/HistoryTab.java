package avsa.presentation;

import avsa.domain.AccidentRecord;
import avsa.domain.ConsistencyWarning;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.VehicleHistory;
import avsa.infrastructure.history.HistoryImportResult;
import avsa.infrastructure.history.HistoryInput;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * F04: import a history report (CARFAX PDF or JSON) or enter the history by hand. Imported values are
 * shown for review and are only stored when the seller saves them.
 */
final class HistoryTab extends FeatureTab {

    private final TextField owners = new TextField();
    private final TextField accidents = new TextField();
    private final TextField serviceRecords = new TextField();
    private final ComboBox<LienStatus> lien = Ui.choice(LienStatus.class, LienStatus.UNKNOWN);
    private final TextField provinces = new TextField();
    private final VBox review = new VBox(4);
    private final VBox current = new VBox(4);
    private final Button save = new Button("Save history");
    private HistoryImportResult imported;

    HistoryTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "History";
    }

    @Override
    Node content() {
        owners.setPromptText("Leave empty if unknown");
        accidents.setPromptText("A number, or empty if unknown");
        serviceRecords.setPromptText("Optional");
        provinces.setPromptText("ON, QC");
        for (TextField f : List.of(owners, accidents, serviceRecords, provinces)) {
            f.setPrefColumnCount(24);
        }
        Button importButton = new Button("Import report...");
        importButton.setOnAction(e -> importReport());
        Button manual = new Button("Enter by hand");
        manual.setOnAction(e -> clear());
        save.setDefaultButton(true);
        save.setOnAction(e -> save());

        GridPane form = Ui.form();
        Ui.row(form, "Owners", owners);
        Ui.row(form, "Reported accidents", accidents);
        Ui.row(form, "Service records", serviceRecords);
        Ui.row(form, "Lien", lien);
        Ui.row(form, "Provinces registered", provinces);
        return Ui.column(Ui.heading("Vehicle history"),
                Ui.muted("A CARFAX PDF or a JSON report is read on this computer; nothing is stored until you save. "
                        + "Values that could not be read are marked \"please confirm\"."),
                new HBox(8, importButton, manual), Ui.card("Review and edit", form, review, save), current);
    }

    private void importReport() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("History reports", "*.pdf", "*.json", "*.txt"));
        File file = chooser.showOpenDialog(ctx.stage());
        if (file == null) {
            return;
        }
        ctx.run("Reading the report...", () -> controller().importHistory(HistoryInput.fromFile(file.toPath())), r -> {
            clear();
            if (r.needsManualEntry()) {
                review.getChildren().add(Ui.warning("The report could not be read: " + r.reason().orElse("")
                        + " Enter the history by hand."));
                return;
            }
            imported = r;
            VehicleHistory h = r.history();
            owners.setText(h.ownerCount().map(String::valueOf).orElse(""));
            accidents.setText(h.accidentsKnown() ? String.valueOf(h.accidents().size()) : "");
            serviceRecords.setText(h.serviceRecordCount().map(String::valueOf).orElse(""));
            lien.setValue(h.lienStatus());
            provinces.setText(String.join(", ", h.registrationProvinces()));
            review.getChildren().add(Ui.ok("Read from " + file.getName() + ". Check the values, then save."));
            h.fieldsToConfirm().forEach(f -> review.getChildren().add(Ui.warning("Please confirm: " + f)));
            for (ConsistencyWarning w : r.report().warnings()) {
                review.getChildren().add(w.blocking() ? Ui.error("Blocking: " + w.message()) : Ui.warning(w.message()));
            }
            h.trustBadges().forEach(b -> review.getChildren().add(Ui.ok("Badge: " + b)));
            save.setDisable(r.report().hasBlockingWarning());
        });
    }

    private void clear() {
        imported = null;
        review.getChildren().clear();
        save.setDisable(false);
        for (TextField f : List.of(owners, accidents, serviceRecords, provinces)) {
            f.clear();
        }
        lien.setValue(LienStatus.UNKNOWN);
    }

    private void save() {
        VehicleHistory history;
        try {
            history = build();
        } catch (IllegalArgumentException e) {
            ctx.problem(e);
            return;
        }
        ctx.run("Saving the history...", () -> {
            controller().confirmHistory(history);
            return history;
        }, h -> {
            clear();
            review.getChildren().add(Ui.ok("History saved. Calculate the price again to include it."));
        });
    }

    /** Keeps what the report said unless the seller changed it; changed fields count as entered by hand. */
    private VehicleHistory build() {
        Integer ownerCount = owners.getText().isBlank() ? null : Ui.integer(owners.getText(), "Owners");
        Integer services = serviceRecords.getText().isBlank() ? null
                : Ui.integer(serviceRecords.getText(), "Service records");
        boolean accidentsKnown = !accidents.getText().isBlank();
        int accidentCount = accidentsKnown ? Ui.integer(accidents.getText(), "Reported accidents") : 0;
        VehicleHistory from = imported == null ? null : imported.history();
        List<AccidentRecord> records = new ArrayList<>();
        if (from != null && from.accidentsKnown() && from.accidents().size() == accidentCount) {
            records.addAll(from.accidents());
        } else {
            for (int i = 0; i < accidentCount; i++) {
                records.add(new AccidentRecord(null, null, "Entered by the seller"));
            }
        }
        return new VehicleHistory(from == null ? null : from.vin().orElse(null), ownerCount, services, lien.getValue(),
                Ui.list(provinces.getText()), from == null ? HistorySourceType.MANUAL : from.sourceType(),
                accidentsKnown, records, from == null ? List.of() : from.odometerReadings());
    }

    @Override
    void refresh() {
        current.getChildren().clear();
        session().flatMap(s -> s.history()).ifPresentOrElse(h -> {
            List<Node> rows = new ArrayList<>();
            rows.add(Ui.text("Owners: " + h.ownerCount().map(String::valueOf).orElse("unknown")
                    + "   Accidents: " + (h.accidentsKnown() ? String.valueOf(h.accidents().size()) : "unknown")
                    + "   Lien: " + Ui.label(h.lienStatus())
                    + "   Provinces: " + (h.registrationProvinces().isEmpty() ? "unknown"
                    : String.join(" -> ", h.registrationProvinces()))
                    + "   Source: " + Ui.label(h.sourceType())));
            h.trustBadges().forEach(b -> rows.add(Ui.ok("Badge: " + b)));
            h.fieldsToConfirm().forEach(f -> rows.add(Ui.warning("Please confirm: " + f)));
            current.getChildren().add(Ui.card("Saved history", rows.toArray(Node[]::new)));
        }, () -> current.getChildren().add(Ui.muted(session().isPresent()
                ? "No history saved yet. Pricing then uses neutral assumptions and lower confidence."
                : "Save a vehicle profile first.")));
    }
}
