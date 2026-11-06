package avsa.presentation;

import avsa.application.AgentController;
import avsa.domain.Session;
import java.util.Optional;
import javafx.scene.Node;

/**
 * One tab of the main window. A tab only calls the facade and shows results; it builds its
 * controls once and is refreshed after every action and whenever another session is opened.
 */
abstract class FeatureTab {

    protected final GuiContext ctx;

    FeatureTab(GuiContext ctx) {
        this.ctx = ctx;
    }

    abstract String title();

    /** Builds the tab's controls; called once. */
    abstract Node content();

    /** Shows the current state of the session. */
    abstract void refresh();

    protected AgentController controller() {
        return ctx.controller();
    }

    protected Optional<Session> session() {
        return ctx.session();
    }
}
