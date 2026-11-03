package avsa.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Observer subject: delivers each event to the listeners subscribed to its type or to any of
 * its supertypes, so subscribing to {@link AgentEvent} receives everything.
 */
public final class EventBus {

    private final Map<Class<? extends AgentEvent>, List<EventListener>> listeners = new LinkedHashMap<>();

    public void subscribe(Class<? extends AgentEvent> type, EventListener listener) {
        listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(listener);
    }

    public void unsubscribe(Class<? extends AgentEvent> type, EventListener listener) {
        List<EventListener> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void publish(AgentEvent event) {
        for (Map.Entry<Class<? extends AgentEvent>, List<EventListener>> entry : listeners.entrySet()) {
            if (entry.getKey().isInstance(event)) {
                for (EventListener listener : List.copyOf(entry.getValue())) {
                    listener.onEvent(event);
                }
            }
        }
    }
}
