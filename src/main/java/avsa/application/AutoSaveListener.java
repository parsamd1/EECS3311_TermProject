package avsa.application;

/** Saves the session on every state-changing event, so nothing is lost if the app closes (F10). */
public final class AutoSaveListener implements EventListener {

    private final SessionService sessions;

    public AutoSaveListener(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public void onEvent(AgentEvent event) {
        sessions.saveCurrent();
    }
}
