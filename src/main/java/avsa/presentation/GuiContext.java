package avsa.presentation;

import avsa.application.AgentController;
import avsa.domain.Session;
import avsa.infrastructure.AppConfig;
import avsa.infrastructure.market.Gazetteer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * What every tab shares: the facade, and one background thread that runs every action so the window
 * stays responsive during a live search or an AI call. While an action runs the tabs are disabled,
 * so the session is only read on the JavaFX thread when nothing is changing it.
 */
final class GuiContext {

    /** Shows and hides the progress overlay. */
    interface BusyView {
        void busy(String message);

        void idle();
    }

    private final AgentController controller;
    private final AppConfig config;
    private final Stage stage;
    private final HostServices host;
    private final Gazetteer gazetteer = new Gazetteer();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "avsa-worker");
        t.setDaemon(true);
        return t;
    });
    private BusyView busyView;
    private Consumer<List<String>> notesView = notes -> { };
    private Runnable afterAction = () -> { };

    GuiContext(AgentController controller, AppConfig config, Stage stage, HostServices host) {
        this.controller = controller;
        this.config = config;
        this.stage = stage;
        this.host = host;
    }

    void bind(BusyView busy, Consumer<List<String>> notes, Runnable refreshAll) {
        this.busyView = busy;
        this.notesView = notes;
        this.afterAction = refreshAll;
    }

    AgentController controller() {
        return controller;
    }

    AppConfig config() {
        return config;
    }

    Stage stage() {
        return stage;
    }

    Gazetteer gazetteer() {
        return gazetteer;
    }

    Optional<Session> session() {
        return controller.sessions().hasCurrent() ? Optional.of(controller.sessions().current()) : Optional.empty();
    }

    void openLink(String url) {
        host.showDocument(url);
    }

    /**
     * Runs {@code work} in the background, then {@code onSuccess} on the JavaFX thread. The notes of the
     * action (for example "the AI was unavailable, the template was used") are shown afterwards, and
     * every tab is refreshed.
     */
    <T> void run(String message, Callable<T> work, Consumer<T> onSuccess) {
        busyView.busy(message);
        worker.submit(() -> {
            try {
                T value = work.call();
                Platform.runLater(() -> {
                    busyView.idle();
                    try {
                        onSuccess.accept(value);
                    } finally {
                        notesView.accept(controller.lastNotes());
                        afterAction.run();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    busyView.idle();
                    problem(e);
                    afterAction.run();
                });
            }
        });
    }

    /** Explains a rejected action: wrong input, a missing earlier step, or an unexpected fault. */
    void problem(Exception e) {
        boolean expected = e instanceof IllegalArgumentException || e instanceof IllegalStateException;
        Alert alert = new Alert(expected ? Alert.AlertType.WARNING : Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(MainWindow.TITLE);
        alert.setHeaderText(e instanceof IllegalArgumentException ? "Please check the input"
                : e instanceof IllegalStateException ? "This step cannot run yet" : "Something went wrong");
        String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(480);
        alert.showAndWait();
    }

    void shutdown() {
        worker.shutdownNow();
    }
}
