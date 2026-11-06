package avsa.presentation;

import avsa.domain.GeneratedListing;
import avsa.domain.ListingEdit;
import avsa.domain.ListingOptions;
import avsa.domain.Tone;
import avsa.domain.Violation;
import java.util.Arrays;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * F07: generate a listing, edit it, and see the validation result. The validator, not the AI, decides
 * whether the listing may be exported.
 */
final class ListingTab extends FeatureTab {

    private final ComboBox<Tone> tone = Ui.choice(Tone.class, Tone.PROFESSIONAL);
    private final CheckBox includeFeatures = new CheckBox("Features");
    private final CheckBox includeMaintenance = new CheckBox("Maintenance");
    private final TextField keywords = new TextField();
    private final TextField title = new TextField();
    private final TextArea description = area(5);
    private final TextArea highlights = area(4);
    private final TextArea maintenance = area(3);
    private final TextArea disclosure = area(3);
    private final VBox status = new VBox(4);
    private final VBox validation = new VBox(4);
    private final Button saveEdits = new Button("Save edits and check again");
    private final Button override = new Button("Export anyway (I accept these warnings)");
    private GeneratedListing shown;

    ListingTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Listing";
    }

    @Override
    Node content() {
        includeFeatures.setSelected(true);
        includeMaintenance.setSelected(true);
        keywords.setPromptText("AWD, one owner");
        GridPane options = Ui.form();
        Ui.row(options, "Tone", tone);
        Ui.row(options, "Include", new HBox(12, includeFeatures, includeMaintenance));
        Ui.row(options, "Keywords", keywords);
        Button generate = new Button("Generate");
        generate.setDefaultButton(true);
        generate.setOnAction(e -> generate(tone.getValue()));
        Button shorter = new Button("Make shorter");
        shorter.setOnAction(e -> generate(Tone.CONCISE));

        GridPane editor = Ui.form();
        Ui.row(editor, "Title", title);
        Ui.row(editor, "Description", description);
        Ui.row(editor, "Highlights (one per line)", highlights);
        Ui.row(editor, "Maintenance (one per line)", maintenance);
        Ui.row(editor, "Condition disclosure", disclosure);
        title.setPrefColumnCount(60);
        saveEdits.setOnAction(e -> saveEdits());
        override.setOnAction(e -> ctx.run("Accepting the warnings...", () -> controller().overrideListingValidation(),
                l -> { }));
        return Ui.column(Ui.heading("Listing"),
                Ui.muted("The AI writes from the vehicle facts only (never the VIN). Every defect must be disclosed and "
                        + "the price and facts must match, or the listing cannot be exported without your override."),
                Ui.card("Options", options, new HBox(8, generate, shorter)), status,
                new HBox(16, Ui.card("Text", editor, saveEdits), Ui.card("Validation", validation, override)));
    }

    private void generate(Tone chosen) {
        ListingOptions options = new ListingOptions(chosen, includeFeatures.isSelected(), includeMaintenance.isSelected(),
                Ui.list(keywords.getText()));
        ctx.run("Writing the listing and checking it...", () -> controller().generateListing(options), l -> { });
    }

    private void saveEdits() {
        ListingEdit edit = new ListingEdit(title.getText().strip(), description.getText().strip(), lines(highlights),
                lines(maintenance), disclosure.getText().strip());
        ctx.run("Checking your edits...", () -> controller().editListing(edit), l -> { });
    }

    @Override
    void refresh() {
        GeneratedListing l = session().flatMap(s -> s.latestListing()).orElse(null);
        status.getChildren().clear();
        validation.getChildren().clear();
        saveEdits.setDisable(l == null);
        override.setVisible(false);
        if (l == null) {
            status.getChildren().add(Ui.muted(session().flatMap(s -> s.activePrice()).isPresent()
                    ? "No listing yet." : "Accept a price in the Pricing tab first; the listing states it."));
            shown = null;
            fill(null);
            return;
        }
        if (l != shown) {
            fill(l);
        }
        shown = l;
        status.getChildren().add(new HBox(8, Ui.source(!l.templateFallback(), "Template (AI unavailable)"),
                Ui.text("Price " + Ui.cad(l.price()) + "   Tone " + Ui.label(l.tone())
                        + (l.editedByUser() ? "   Edited by you" : ""))));
        l.report().ifPresent(r -> {
            if (r.isValid()) {
                validation.getChildren().addAll(Ui.ok("✔ All defects disclosed"), Ui.ok("✔ No banned claims"),
                        Ui.ok("✔ Facts and price match the profile"));
            } else {
                for (Violation v : r.violations()) {
                    validation.getChildren().add(Ui.error("✘ " + v.message()));
                }
            }
        });
        validation.getChildren().add(l.isExportable()
                ? Ui.ok("Ready to export" + (l.validationOverridden() ? " (you accepted the warnings)" : ""))
                : Ui.error("Not exportable yet"));
        override.setVisible(!l.isExportable());
    }

    private void fill(GeneratedListing l) {
        title.setText(l == null ? "" : l.title());
        description.setText(l == null ? "" : l.description());
        highlights.setText(l == null ? "" : String.join("\n", l.highlights()));
        maintenance.setText(l == null ? "" : String.join("\n", l.maintenanceDetails()));
        disclosure.setText(l == null ? "" : l.conditionDisclosure());
    }

    private static List<String> lines(TextArea area) {
        return Arrays.stream(area.getText().split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static TextArea area(int rows) {
        TextArea area = new TextArea();
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        area.setPrefColumnCount(60);
        return area;
    }
}
