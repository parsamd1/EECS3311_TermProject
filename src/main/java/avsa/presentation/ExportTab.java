package avsa.presentation;

import avsa.domain.ExportedListing;
import avsa.domain.Platform;
import avsa.services.ListingExporter;
import java.io.File;
import java.util.OptionalInt;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** F08: preview the listing for one platform with character counters, then copy it or save it as a file. */
final class ExportTab extends FeatureTab {

    private final ComboBox<Platform> platform = new ComboBox<>();
    private final TextArea preview = new TextArea();
    private final VBox checks = new VBox(4);
    private final Button copy = new Button("Copy");
    private final Button saveAs = new Button("Save as file...");
    private ExportedListing exported;

    ExportTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Export";
    }

    @Override
    Node content() {
        platform.getItems().setAll(Platform.values());
        platform.setConverter(new StringConverter<>() {
            @Override
            public String toString(Platform p) {
                return p == null ? "" : p.displayName();
            }

            @Override
            public Platform fromString(String s) {
                return null;
            }
        });
        platform.setValue(Platform.AUTOTRADER);
        Button show = new Button("Preview");
        show.setDefaultButton(true);
        show.setOnAction(e -> ctx.run("Formatting for " + platform.getValue().displayName() + "...",
                () -> controller().exportListing(platform.getValue(), null), this::show));
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(exported.text());
            Clipboard.getSystemClipboard().setContent(content);
            checks.getChildren().add(Ui.ok("Copied. Paste it into " + exported.profile().platform().displayName() + "."));
        });
        saveAs.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName(platform.getValue().name().toLowerCase() + "-listing.txt");
            File file = chooser.showSaveDialog(ctx.stage());
            if (file != null) {
                ctx.run("Saving...", () -> controller().exportListing(platform.getValue(), file.toPath()), x -> {
                    show(x);
                    checks.getChildren().add(Ui.ok("Saved to " + file));
                });
            }
        });
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(18);
        preview.setPrefColumnCount(70);
        return Ui.column(Ui.heading("Export"),
                Ui.muted("Formats the latest listing for one marketplace. Optional sections are removed in a fixed order "
                        + "if the text is too long; the condition disclosure and price are never removed."),
                new HBox(8, new Label("Platform"), platform, show), new HBox(16, preview, checks),
                new HBox(8, copy, saveAs));
    }

    private void show(ExportedListing e) {
        exported = e;
        preview.setText(e.text());
        checks.getChildren().clear();
        String title = e.fields().getOrDefault(ListingExporter.TITLE, "");
        checks.getChildren().add(counter("Title", title.length(), e.profile().limitOf(ListingExporter.TITLE)));
        checks.getChildren().add(counter("Description box", ListingExporter.bodyLength(e.fields()),
                e.profile().limitOf(ListingExporter.BODY)));
        checks.getChildren().add(Ui.ok("✔ Condition disclosure included"));
        checks.getChildren().add(Ui.ok("✔ Price included"));
        if (!e.trimmedSections().isEmpty()) {
            checks.getChildren().add(Ui.warning("Removed to fit: " + String.join(", ", e.trimmedSections())));
        }
        e.warnings().forEach(w -> checks.getChildren().add(Ui.warning(w)));
        updateButtons();
    }

    private static Label counter(String name, int length, OptionalInt limit) {
        if (limit.isEmpty()) {
            return Ui.text(name + ": " + length + " characters");
        }
        String text = name + ": " + length + " / " + limit.getAsInt() + " characters";
        return length <= limit.getAsInt() ? Ui.ok("✔ " + text) : Ui.error("✘ " + text);
    }

    private void updateButtons() {
        copy.setDisable(exported == null);
        saveAs.setDisable(exported == null);
    }

    @Override
    void refresh() {
        boolean current = exported != null && session().flatMap(s -> s.latestListing())
                .map(l -> l == exported.listing()).orElse(false);
        if (!current) {
            exported = null;
            preview.clear();
            checks.getChildren().setAll(Ui.muted(session().flatMap(s -> s.latestListing()).isPresent()
                    ? "Choose a platform and preview." : "Generate a listing in the Listing tab first."));
        }
        updateButtons();
    }
}
