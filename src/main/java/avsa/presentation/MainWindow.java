package avsa.presentation;

import avsa.application.AgentController;
import avsa.application.AgentEvent;
import avsa.application.AvsaApplication;
import avsa.application.EventListener;
import avsa.domain.Session;
import avsa.infrastructure.AppConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX front end. Like the CLI it only talks to the {@link AgentController} facade, and as an
 * observer of the event bus it shows when each change has been saved.
 */
public class MainWindow extends Application implements EventListener {

    static final String TITLE = "AVSA - AI Vehicle Sales Assistant";

    private final List<FeatureTab> tabs = new ArrayList<>();
    private GuiContext ctx;
    private CurrentSessionFile currentSession;
    private final Label vehicleStatus = new Label();
    private final Label saveStatus = new Label("All changes saved");
    private final Label aiStatus = new Label();
    private final Label notes = Ui.text("");
    private final Label busyMessage = Ui.text("");
    private final Label busyElapsed = Ui.muted("");
    private final StackPane overlay = new StackPane();
    private TabPane tabPane;
    private Timeline elapsedTimer;

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        Path dataDir = AvsaCli.dataDir();
        AgentController controller = AvsaApplication.create(config, dataDir, Clock.systemDefaultZone(), null);
        controller.events().subscribe(AgentEvent.class, this);
        currentSession = new CurrentSessionFile(dataDir);
        currentSession.restore(controller.sessions());

        ctx = new GuiContext(controller, config, stage, getHostServices());
        ctx.bind(new GuiContext.BusyView() {
            @Override
            public void busy(String message) {
                showBusy(message);
            }

            @Override
            public void idle() {
                hideBusy();
            }
        }, this::showNotes, this::refreshAll);

        tabs.addAll(List.of(new VehicleTab(ctx), new HistoryTab(ctx), new MarketTab(ctx), new PricingTab(ctx),
                new PositioningTab(ctx), new ForecastTab(ctx), new ListingTab(ctx), new ExportTab(ctx), new OffersTab(ctx),
                new SimulatorTab(ctx), new SessionsTab(ctx)));
        tabPane = new TabPane();
        for (FeatureTab feature : tabs) {
            ScrollPane scroll = new ScrollPane(feature.content());
            scroll.setFitToWidth(true);
            Tab tab = new Tab(feature.title(), scroll);
            tab.setClosable(false);
            tabPane.getTabs().add(tab);
        }

        BorderPane main = new BorderPane(tabPane);
        notes.setMaxWidth(Double.MAX_VALUE);
        HBox notesBar = Ui.styled(new HBox(notes), "notes-bar");
        notesBar.visibleProperty().bind(notes.textProperty().isNotEmpty());
        notesBar.managedProperty().bind(notesBar.visibleProperty());
        main.setTop(notesBar);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusBar = Ui.styled(new HBox(16, vehicleStatus, spacer, saveStatus, aiStatus), "status-bar");
        main.setBottom(statusBar);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);
        VBox busyBox = Ui.styled(new VBox(12, spinner, busyMessage, busyElapsed), "busy-box");
        busyBox.setAlignment(Pos.CENTER);
        busyBox.setMaxSize(460, Region.USE_PREF_SIZE);
        busyMessage.setMaxWidth(420);
        overlay.getChildren().add(busyBox);
        overlay.getStyleClass().add("busy-overlay");
        overlay.setVisible(false);

        StackPane root = new StackPane(main, overlay);
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(MainWindow.class.getResource("avsa.css").toExternalForm());
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            controller.sessions().saveCurrent();
            ctx.shutdown();
        });
        refreshAll();
        if (!controller.sessions().hasCurrent()) {
            showNotes(List.of("Start in the Vehicle tab: describe the car you are selling, then save the profile."));
        }
        stage.show();
    }

    /** Called by the event bus, possibly from the worker thread. */
    @Override
    public void onEvent(AgentEvent event) {
        Platform.runLater(() -> saveStatus.setText(ctx.controller().sessions().hasUnsavedChanges()
                ? "Not saved: the data folder cannot be written; changes are kept until it can"
                : "All changes saved " + Ui.when(event.occurredAt())));
    }

    private void refreshAll() {
        for (FeatureTab tab : tabs) {
            tab.refresh();
        }
        vehicleStatus.setText(ctx.session().map(MainWindow::describe).orElse("No vehicle yet"));
        aiStatus.setText(ctx.config().llmApiKey().isPresent() ? "AI: " + ctx.config().llmModelId()
                : "AI off (no API key): template fallbacks are labelled");
        try {
            currentSession.remember(ctx.controller().sessions());
        } catch (IOException e) {
            saveStatus.setText("Could not remember the open session: " + e.getMessage());
        }
    }

    private static String describe(Session s) {
        return s.name() + (s.readOnly() ? " (read-only: saved by a newer version)" : "");
    }

    private void showNotes(List<String> list) {
        notes.setText(list.isEmpty() ? "" : "Note: " + String.join("\nNote: ", list));
    }

    private void showBusy(String message) {
        notes.setText("");
        tabPane.setDisable(true);
        busyMessage.setText(message);
        Instant started = Instant.now();
        busyElapsed.setText("");
        elapsedTimer = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            long s = Duration.between(started, Instant.now()).toSeconds();
            busyElapsed.setText(s + " s");
            overlay.setVisible(true);
        }));
        elapsedTimer.setCycleCount(Timeline.INDEFINITE);
        elapsedTimer.play();
    }

    private void hideBusy() {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
        }
        overlay.setVisible(false);
        tabPane.setDisable(false);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
