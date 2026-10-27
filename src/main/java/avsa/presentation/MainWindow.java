package avsa.presentation;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * JavaFX front end. Feature tabs are added as the features are implemented;
 * like the CLI, the window only talks to the application facade.
 */
public class MainWindow extends Application {

    static final String TITLE = "AVSA - AI Vehicle Sales Assistant";

    @Override
    public void start(Stage stage) {
        Label status = new Label("No vehicle profile loaded.");
        BorderPane root = new BorderPane(status);
        root.setPadding(new Insets(16));

        stage.setTitle(TITLE);
        stage.setScene(new Scene(root, 1000, 700));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
