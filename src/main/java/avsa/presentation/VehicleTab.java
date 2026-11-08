package avsa.presentation;

import avsa.domain.BodyStyle;
import avsa.domain.ConditionGrade;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.Location;
import avsa.domain.MaintenanceRecord;
import avsa.domain.SessionSummary;
import avsa.domain.Severity;
import avsa.domain.Transmission;
import avsa.domain.ValidationError;
import avsa.domain.VehicleProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** F01: the vehicle form, its condition report and maintenance records. */
final class VehicleTab extends FeatureTab {

    private static final List<String> PROVINCES = List.of("ON", "QC", "BC", "AB", "MB", "SK", "NS", "NB", "NL", "PE",
            "YT", "NT", "NU");
    private static final String GRADE_NOT_SET = "Not set (use the grade the defects suggest)";

    private final TextField make = new TextField();
    private final TextField model = new TextField();
    private final TextField year = new TextField();
    private final TextField trim = new TextField();
    private final ComboBox<BodyStyle> body = Ui.choice(BodyStyle.class, null);
    private final TextField km = new TextField();
    private final ComboBox<Transmission> transmission = Ui.choice(Transmission.class, Transmission.AUTOMATIC);
    private final ComboBox<FuelType> fuel = Ui.choice(FuelType.class, FuelType.GASOLINE);
    private final TextField city = new TextField();
    private final ComboBox<String> province = new ComboBox<>(FXCollections.observableArrayList(PROVINCES));
    private final TextField vin = new TextField();
    private final TextField features = new TextField();
    private final ComboBox<String> grade = new ComboBox<>();
    private final ObservableList<Defect> defects = FXCollections.observableArrayList();
    private final ObservableList<MaintenanceRecord> maintenance = FXCollections.observableArrayList();
    private final VBox messages = new VBox(4);
    private final VBox derived = new VBox(4);
    private VehicleProfile shown;

    VehicleTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Vehicle";
    }

    @Override
    Node content() {
        province.setValue("ON");
        grade.getItems().add(GRADE_NOT_SET);
        for (ConditionGrade g : ConditionGrade.values()) {
            grade.getItems().add(Ui.label(g));
        }
        grade.setValue(GRADE_NOT_SET);
        make.setPromptText("Mazda");
        model.setPromptText("CX-5");
        year.setPromptText("2022");
        trim.setPromptText("GS");
        km.setPromptText("49,000");
        city.setPromptText("Toronto");
        vin.setPromptText("Optional; stays on this computer and is never sent to the AI");
        features.setPromptText("Winter tires, heated seats");

        GridPane form = Ui.form();
        Ui.row(form, "Make", make);
        Ui.row(form, "Model", model);
        Ui.row(form, "Year", year);
        Ui.row(form, "Trim", trim);
        Ui.row(form, "Body style", body);
        Ui.row(form, "Odometer (km)", km);
        Ui.row(form, "Transmission", transmission);
        Ui.row(form, "Fuel", fuel);
        Ui.row(form, "City", city);
        Ui.row(form, "Province", province);
        Ui.row(form, "VIN", vin);
        Ui.row(form, "Features", features);
        vin.setPrefColumnCount(32);

        TableView<Defect> defectTable = Ui.table(140, List.of(
                Ui.col("Type", d -> Ui.label(d.type()), 90),
                Ui.col("Where", Defect::bodyLocation, 120),
                Ui.col("Severity", d -> Ui.label(d.severity()), 90),
                Ui.col("Repair estimate", d -> d.estimate().map(Ui::cad).orElse(""), 110),
                Ui.col("Note", Defect::note, 200)));
        defectTable.setItems(defects);
        Button addDefect = new Button("Add defect...");
        addDefect.setOnAction(e -> askDefect().ifPresent(defects::add));
        Button removeDefect = new Button("Remove");
        removeDefect.setOnAction(e -> defects.remove(defectTable.getSelectionModel().getSelectedItem()));
        removeDefect.disableProperty().bind(defectTable.getSelectionModel().selectedItemProperty().isNull());
        GridPane gradeRow = Ui.form();
        Ui.row(gradeRow, "Overall grade", grade);
        VBox condition = Ui.card("Condition", gradeRow, defectTable, new HBox(8, addDefect, removeDefect));

        TableView<MaintenanceRecord> serviceTable = Ui.table(110, List.of(
                Ui.col("Date", r -> String.valueOf(r.date()), 100),
                Ui.col("Odometer", r -> Ui.km(r.odometerKm()), 100),
                Ui.col("Work done", MaintenanceRecord::description, 300)));
        serviceTable.setItems(maintenance);
        Button addService = new Button("Add record...");
        addService.setOnAction(e -> askMaintenance().ifPresent(maintenance::add));
        Button removeService = new Button("Remove");
        removeService.setOnAction(e -> maintenance.remove(serviceTable.getSelectionModel().getSelectedItem()));
        removeService.disableProperty().bind(serviceTable.getSelectionModel().selectedItemProperty().isNull());
        VBox service = Ui.card("Recent maintenance", serviceTable, new HBox(8, addService, removeService));

        Button save = new Button("Save profile");
        save.setDefaultButton(true);
        save.setOnAction(e -> save());
        Button clear = new Button("New vehicle");
        clear.setOnAction(e -> fill(null));

        HBox top = new HBox(24, Ui.card("Vehicle", form), new VBox(12, condition, service));
        return Ui.column(Ui.heading("Vehicle profile"),
                Ui.muted("Everything else reads from this profile. Defects you list here must be disclosed in the listing."),
                top, new HBox(8, save, clear), messages, derived);
    }

    @Override
    void refresh() {
        Optional<VehicleProfile> current = session().map(s -> s.activeProfile());
        if (current.isPresent() && current.get() != shown) {
            fill(current.get());
        }
        derived.getChildren().clear();
        current.ifPresent(p -> {
            ConditionReport c = p.condition();
            derived.getChildren().add(Ui.card("Derived values",
                    Ui.text("Age: " + p.ageInYears(LocalDate.now()) + " years   Segment: " + Ui.label(p.segment())
                            + "   Damage severity score: " + c.severityScore()),
                    Ui.text("Condition grade used: " + Ui.label(c.effectiveGrade())
                            + (c.overallGrade().isEmpty() ? " (suggested from the defects)" : ""))));
            if (c.gradeConflictsWithDefects()) {
                derived.getChildren().add(Ui.warning("The grade you chose is better than the defects suggest ("
                        + Ui.label(c.suggestedGrade()) + "). Buyers may disagree with it."));
            }
        });
    }

    private void fill(VehicleProfile p) {
        shown = p;
        messages.getChildren().clear();
        make.setText(p == null ? "" : p.make());
        model.setText(p == null ? "" : p.model());
        year.setText(p == null ? "" : String.valueOf(p.year()));
        trim.setText(p == null ? "" : p.trim());
        body.setValue(p == null ? null : p.bodyStyle());
        km.setText(p == null ? "" : String.format("%,d", p.odometerKm()));
        transmission.setValue(p == null ? Transmission.AUTOMATIC : p.transmission());
        fuel.setValue(p == null ? FuelType.GASOLINE : p.fuelType());
        city.setText(p == null ? "" : p.location().city());
        province.setValue(p == null ? "ON" : p.location().province());
        vin.setText(p == null ? "" : p.vin().orElse(""));
        features.setText(p == null ? "" : String.join(", ", p.features()));
        grade.setValue(p == null ? GRADE_NOT_SET : p.condition().overallGrade().map(Ui::label).orElse(GRADE_NOT_SET));
        defects.setAll(p == null ? List.of() : p.condition().defects());
        maintenance.setAll(p == null ? List.of() : p.maintenanceRecords());
    }

    private void save() {
        messages.getChildren().clear();
        VehicleProfile profile;
        try {
            profile = build();
        } catch (IllegalArgumentException e) {
            messages.getChildren().add(Ui.error(e.getMessage()));
            return;
        }
        List<ValidationError> problems = profile.validate(LocalDate.now());
        problems.stream().filter(ValidationError::blocking).forEach(p -> messages.getChildren().add(Ui.error(p.message())));
        if (VehicleProfile.hasBlockingErrors(problems)) {
            return;
        }
        boolean sameAsOpen = session().map(s -> s.activeProfile().sameVehicleAs(profile)).orElse(false);
        if (!sameAsOpen) {
            Optional<SessionSummary> saved = controller().sessions().findMatching(profile);
            if (saved.isPresent() && confirm("A saved session for this vehicle already exists (" + saved.get().name()
                    + "). Open it instead of starting a new one?")) {
                ctx.run("Opening the saved session...", () -> controller().sessions().open(saved.get().id()), s -> { });
                return;
            }
        }
        List<ValidationError> warnings = problems.stream().filter(p -> !p.blocking()).toList();
        ctx.run("Saving the profile...", () -> controller().saveProfile(profile), p -> {
            // The form already shows this profile; without this, the refresh after saving refills the
            // form and clears the messages below.
            shown = p;
            messages.getChildren().add(Ui.ok("Profile saved."));
            warnings.forEach(w -> messages.getChildren().add(Ui.warning(w.message())));
        });
    }

    private VehicleProfile build() {
        if (body.getValue() == null) {
            throw new IllegalArgumentException("Choose a body style.");
        }
        Location location = ctx.gazetteer().resolve(city.getText().strip(), province.getValue()).orElseThrow(() ->
                new IllegalArgumentException("Unknown city: " + city.getText().strip() + ", " + province.getValue()
                        + ". Use the nearest larger town."));
        ConditionGrade chosen = grade.getValue().equals(GRADE_NOT_SET) ? null
                : ConditionGrade.values()[grade.getItems().indexOf(grade.getValue()) - 1];
        return VehicleProfile.builder()
                .make(make.getText().strip()).model(model.getText().strip())
                .year(Ui.integer(year.getText(), "Year")).trim(trim.getText().strip())
                .bodyStyle(body.getValue()).odometerKm(Ui.integer(km.getText(), "Odometer"))
                .transmission(transmission.getValue()).fuelType(fuel.getValue()).location(location)
                .vin(vin.getText().isBlank() ? null : vin.getText().strip())
                .features(Ui.list(features.getText())).maintenanceRecords(List.copyOf(maintenance))
                .condition(new ConditionReport(chosen, List.copyOf(defects)))
                .build();
    }

    private Optional<Defect> askDefect() {
        ComboBox<DefectType> type = Ui.choice(DefectType.class, DefectType.SCRATCH);
        TextField where = new TextField();
        where.setPromptText("rear door");
        ComboBox<Severity> severity = Ui.choice(Severity.class, Severity.MINOR);
        TextField note = new TextField();
        note.setPromptText("About 25 cm hairline, no dent");
        TextField estimate = new TextField();
        estimate.setPromptText("Optional, such as 300");
        GridPane grid = Ui.form();
        Ui.row(grid, "Type", type);
        Ui.row(grid, "Where on the vehicle", where);
        Ui.row(grid, "Severity", severity);
        Ui.row(grid, "Note", note);
        Ui.row(grid, "Repair estimate ($)", estimate);
        return ask("Add defect", grid, () -> {
            if (where.getText().isBlank()) {
                throw new IllegalArgumentException("Say where the defect is, such as \"rear door\".");
            }
            return new Defect(type.getValue(), where.getText(), severity.getValue(), note.getText(),
                    Ui.parseMoney(estimate.getText(), "Repair estimate"));
        });
    }

    private Optional<MaintenanceRecord> askMaintenance() {
        DatePicker date = new DatePicker(LocalDate.now());
        TextField odometer = new TextField();
        TextField work = new TextField();
        work.setPromptText("Oil change and tire rotation");
        GridPane grid = Ui.form();
        Ui.row(grid, "Date", date);
        Ui.row(grid, "Odometer (km)", odometer);
        Ui.row(grid, "Work done", work);
        return ask("Add maintenance record", grid, () -> {
            if (work.getText().isBlank() || date.getValue() == null) {
                throw new IllegalArgumentException("Give the date and the work done.");
            }
            return new MaintenanceRecord(date.getValue(), Ui.integer(odometer.getText(), "Odometer"), work.getText().strip());
        });
    }

    /** Shows a small form until it is valid or cancelled. */
    private <T> Optional<T> ask(String title, Node form, java.util.function.Supplier<T> result) {
        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(ctx.stage());
            dialog.setTitle(title);
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return Optional.empty();
            }
            try {
                return Optional.of(result.get());
            } catch (IllegalArgumentException e) {
                ctx.problem(e);
            }
        }
    }

    private boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO);
        alert.initOwner(ctx.stage());
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}
