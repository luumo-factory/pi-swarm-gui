package ai.luumo.tools.pi.piswarm.gui.swarm;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmListener;

import javax.swing.SwingUtilities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, EDT-facing aggregate of all swarm state. Receives raw callbacks
 * from {@link ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmClient} on MQTT threads and
 * re-dispatches them to UI listeners on the Swing event dispatch thread.
 */
public final class SwarmModel implements SwarmListener {

    private final AppConfig config;

    // All mutable state below is touched only on the EDT.
    private final Map<String, Agent> agents = new LinkedHashMap<>();
    private final Map<String, Deque<AgentEvent>> events = new LinkedHashMap<>();
    private final Deque<BoardPost> board = new ArrayDeque<>();
    private final List<SwarmModelListener> listeners = new CopyOnWriteArrayList<>();

    private boolean connected;

    public SwarmModel(AppConfig config) {
        this.config = config;
    }

    public void addListener(SwarmModelListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SwarmModelListener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return connected;
    }

    /** Snapshot of agents sorted by name; call on the EDT. */
    public List<Agent> agentsSorted() {
        List<Agent> list = new ArrayList<>(agents.values());
        list.sort(Comparator.comparing(Agent::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Agent agent(String id) {
        return agents.get(id);
    }

    /** Recent events for an agent (oldest first); call on the EDT. */
    public List<AgentEvent> eventsFor(String agentId) {
        Deque<AgentEvent> q = events.get(agentId);
        return q == null ? List.of() : new ArrayList<>(q);
    }

    /** Recent board posts (oldest first); call on the EDT. */
    public List<BoardPost> boardPosts() {
        return new ArrayList<>(board);
    }

    // ------------------------------------------------------------------
    // SwarmListener (MQTT threads) -> EDT
    // ------------------------------------------------------------------

    @Override
    public void onConnected() {
        edt(() -> {
            connected = true;
            listeners.forEach(l -> l.connectionChanged(true));
        });
    }

    @Override
    public void onConnectionLost(Throwable cause) {
        edt(() -> {
            connected = false;
            listeners.forEach(l -> l.connectionChanged(false));
        });
    }

    @Override
    public void onAgentUpdated(Agent agent) {
        edt(() -> {
            agents.put(agent.getId(), agent);
            listeners.forEach(l -> l.agentsChanged());
            listeners.forEach(l -> l.agentUpdated(agent));
        });
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        edt(() -> {
            Deque<AgentEvent> q = events.computeIfAbsent(event.agentId(), k -> new ArrayDeque<>());
            q.addLast(event);
            int cap = config.getUi().getEventBufferSize();
            while (q.size() > cap) {
                q.removeFirst();
            }
            listeners.forEach(l -> l.agentEvent(event));
        });
    }

    @Override
    public void onBoardPost(BoardPost post) {
        edt(() -> {
            board.addLast(post);
            int cap = config.getUi().getBoardHistorySize();
            while (board.size() > cap) {
                board.removeFirst();
            }
            listeners.forEach(l -> l.boardPost(post));
        });
    }

    private static void edt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** UI-facing listener; all callbacks fire on the EDT. */
    public interface SwarmModelListener {
        default void connectionChanged(boolean connected) {
        }

        default void agentsChanged() {
        }

        default void agentUpdated(Agent agent) {
        }

        default void agentEvent(AgentEvent event) {
        }

        default void boardPost(BoardPost post) {
        }
    }

    /** Convenience for callers that only want agent collection snapshots. */
    public Collection<Agent> agents() {
        return agents.values();
    }
}
