package avsa.application;

/** Observer: is told about events it subscribed to. */
public interface EventListener {
    void onEvent(AgentEvent event);
}
