package avsa.presentation;

import avsa.domain.CheckpointDecision;
import avsa.domain.Plan;
import avsa.domain.PlanStatus;
import avsa.domain.PlanStep;
import avsa.domain.StepType;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * F12: the Assistant side panel. The seller types a goal (or picks a quick action), the AI plans the steps,
 * the plan view shows each step's status, and an approval dialog appears at each checkpoint.
 */
final class AssistantPanel extends FeatureTab {

    private static final List<String> QUICK_ACTIONS = List.of(
            "Price my car for a quick sale and write a Kijiji ad",
            "Get the best price and prepare an AutoTrader ad",
            "Build the full sales package for AutoTrader, Kijiji and Facebook Marketplace",
            "What is my car worth today, and what if I wait?");
    private static final ButtonType APPROVE = new ButtonType("Approve", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CHANGE = new ButtonType("Change strategy", ButtonBar.ButtonData.OTHER);
    private static final ButtonType REJECT = new ButtonType("Reject", ButtonBar.ButtonData.NO);
    private static final ButtonType LATER = new ButtonType("Decide later", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final TextArea goal = new TextArea();
    private final CheckBox autoApprove = new CheckBox("Approve checkpoints automatically");
    private final VBox planView = new VBox(6);
    private final Label status = Ui.text("");
    private final Button answer = new Button("Answer checkpoint...");
    private final Button resume = new Button("Resume");
    private final Button cancel = new Button("Cancel plan");

    AssistantPanel(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Assistant";
    }

    @Override
    Node content() {
        goal.setPromptText("Tell the assistant what you want, for example: price my car for a quick sale and "
                + "write a Kijiji ad");
        goal.setWrapText(true);
        goal.setPrefRowCount(3);
        Button run = new Button("Plan and run");
        run.setDefaultButton(false);
        run.setOnAction(e -> start(goal.getText()));
        VBox quick = new VBox(4);
        for (String action : QUICK_ACTIONS) {
            Button b = new Button(action);
            b.setWrapText(true);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> {
                goal.setText(action);
                start(action);
            });
            quick.getChildren().add(b);
        }
        answer.setOnAction(e -> askCheckpoint());
        resume.setOnAction(e -> ctx.run("Resuming the plan...", () -> controller().resumeRun(), this::afterAction,
                () -> controller().cancelRun()));
        cancel.setOnAction(e -> ctx.run("Cancelling...", () -> controller().cancelRun(), p -> { }));
        VBox box = new VBox(10, Ui.heading("Assistant"),
                Ui.muted("The AI plans the steps from a fixed catalog; the program checks the plan and runs each step "
                        + "with the same features as the tabs. It stops for your approval at the price and the "
                        + "listing."),
                goal, autoApprove, run, Ui.styled(new Label("Quick actions"), "card-title"), quick,
                Ui.styled(new Label("Plan"), "card-title"), planView, status, new HBox(6, answer, resume, cancel));
        box.setPadding(new javafx.geometry.Insets(12));
        return box;
    }

    private void start(String text) {
        boolean auto = autoApprove.isSelected();
        ctx.run("The assistant is planning the steps...", () -> controller().runGoal(text, auto), this::afterAction,
                () -> controller().cancelRun());
    }

    /** After the plan ran as far as it could: ask at a checkpoint, once the tabs show the new results. */
    private void afterAction(Plan plan) {
        if (plan.status() == PlanStatus.AWAITING_APPROVAL) {
            Platform.runLater(this::askCheckpoint);
        }
    }

    private void askCheckpoint() {
        Optional<Plan> plan = controller().currentPlan().filter(p -> p.status() == PlanStatus.AWAITING_APPROVAL);
        if (plan.isEmpty()) {
            return;
        }
        PlanStep step = plan.get().awaitingStep().orElseThrow();
        boolean price = step.type() == StepType.RECOMMEND_PRICE;
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.initOwner(ctx.stage());
        dialog.setTitle("Checkpoint");
        dialog.setHeaderText(price ? "Approve this price?" : "Approve this listing?");
        dialog.setContentText(step.resultSummary() + "\n\n" + (price
                ? "Approve to make it your active price. Change strategy prices it for "
                + (plan.get().strategy() == avsa.domain.StrategyType.MAX_PROFIT ? "a quick sale" : "maximum profit")
                + " instead. The Pricing tab shows the details."
                : "The full text is in the Listing tab, where you can also edit it. Approving a listing with "
                + "validation warnings accepts them."));
        dialog.getDialogPane().setMinWidth(560);
        dialog.getButtonTypes().setAll(price ? List.of(APPROVE, CHANGE, REJECT, LATER) : List.of(APPROVE, REJECT, LATER));
        ButtonType choice = dialog.showAndWait().orElse(LATER);
        CheckpointDecision decision = choice == APPROVE ? CheckpointDecision.APPROVE
                : choice == CHANGE ? CheckpointDecision.CHANGE_STRATEGY
                : choice == REJECT ? CheckpointDecision.REJECT : null;
        if (decision != null) {
            ctx.run("Continuing the plan...", () -> controller().answerCheckpoint(decision), this::afterAction,
                    () -> controller().cancelRun());
        }
    }

    @Override
    void refresh() {
        planView.getChildren().clear();
        Optional<Plan> current = controller().currentPlan();
        answer.setDisable(current.map(p -> p.status() != PlanStatus.AWAITING_APPROVAL).orElse(true));
        resume.setDisable(current.map(p -> p.status() != PlanStatus.PAUSED).orElse(true));
        cancel.setDisable(current.map(Plan::isFinished).orElse(true));
        if (current.isEmpty()) {
            status.setText(session().isPresent() ? "No plan yet." : "Save a vehicle profile first.");
            return;
        }
        Plan plan = current.get();
        planView.getChildren().add(Ui.muted("\"" + plan.goal() + "\""));
        planView.getChildren().add(new HBox(6, Ui.source(!plan.templatePlan(), "Template plan (AI unavailable)"),
                Ui.text(Ui.label(plan.strategy()) + (plan.platforms().isEmpty() ? "" : " · " + String.join(", ",
                        plan.platforms().stream().map(avsa.domain.Platform::displayName).toList())))));
        for (PlanStep step : plan.steps()) {
            Label line = Ui.text(icon(step) + " " + Ui.label(step.type()) + (step.checkpoint() ? " (checkpoint)" : ""));
            line.getStyleClass().add("card-title");
            VBox row = new VBox(2, line);
            if (!step.resultSummary().isBlank()) {
                row.getChildren().add(Ui.muted(step.resultSummary()));
            }
            planView.getChildren().add(row);
        }
        plan.notes().forEach(n -> planView.getChildren().add(Ui.muted("Note: " + n)));
        status.setText(Ui.label(plan.status()) + (plan.message().isBlank() ? "" : ": " + plan.message()));
        status.getStyleClass().setAll("label", plan.status() == PlanStatus.COMPLETED ? "ok"
                : plan.status() == PlanStatus.PAUSED || plan.status() == PlanStatus.AWAITING_APPROVAL ? "warning"
                : "muted");
    }

    private static String icon(PlanStep step) {
        return switch (step.status()) {
            case PENDING -> "○";
            case RUNNING -> "◐";
            case DONE -> "✔";
            case FAILED -> "✘";
            case SKIPPED -> "–";
        };
    }
}
