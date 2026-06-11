package ai.luumo.tools.pi.piswarm.gui.swarm;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentGroup;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.model.ConsoleInfo;
import ai.luumo.tools.pi.piswarm.gui.model.RawMessage;
import ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmListener;
import com.fasterxml.jackson.databind.JsonNode;

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
    private final Map<String, ConsoleInfo> consoles = new LinkedHashMap<>();
    private final Map<String, Deque<AgentEvent>> events = new LinkedHashMap<>();
    // One scrollback per group board, keyed by group id; all eight are cached so
    // any can be displayed (or persisted) immediately.
    private final Map<String, Deque<BoardPost>> boards = new LinkedHashMap<>();
    // The group each agent currently belongs to. Authoritative for the GUI: it is
    // updated from a registry payload that actually carries a group, and set
    // optimistically when the user picks a new group (the registry republish then
    // confirms it). Agents we've never assigned default to red.
    private final Map<String, AgentGroup> agentGroups = new LinkedHashMap<>();
    // Raw MQTT frames retained per topic for the debug view (insertion-ordered).
    private final Map<String, Deque<RawMessage>> rawByTopic = new LinkedHashMap<>();
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

    /**
     * Agents to display, on the EDT. Excludes agents we've never received a real
     * status update for (stale/retained topics), and sorts live agents first
     * (by name) with offline agents pushed to the bottom.
     */
    public List<Agent> agentsSorted() {
        List<Agent> list = new ArrayList<>();
        for (Agent a : agents.values()) {
            if (a.isStatusKnown()) {
                list.add(a);
            }
        }
        list.sort(Comparator.comparing((Agent a) -> !a.getStatus().isLive())
                .thenComparing(Agent::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Agent agent(String id) {
        return agents.get(id);
    }

    /** The group an agent currently belongs to (defaults to red); call on the EDT. */
    public AgentGroup groupOf(String agentId) {
        return agentGroups.getOrDefault(agentId, AgentGroup.DEFAULT);
    }

    /**
     * Optimistically move an agent to a group (the actual control message is sent
     * by the caller). Fires listeners so the icon/list colour updates immediately;
     * the agent's registry republish later confirms it.
     */
    public void setAgentGroup(String agentId, AgentGroup group) {
        edt(() -> {
            agentGroups.put(agentId, group);
            Agent a = agents.get(agentId);
            if (a != null) {
                a.setGroup(group);
            }
            listeners.forEach(l -> l.agentsChanged());
            if (a != null) {
                listeners.forEach(l -> l.agentUpdated(a));
            }
        });
    }

    /** Snapshot of known consoles (spawn hosts) sorted by name; call on the EDT. */
    public List<ConsoleInfo> consolesSorted() {
        List<ConsoleInfo> list = new ArrayList<>(consoles.values());
        list.sort(Comparator.comparing(ConsoleInfo::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Recent events for an agent (oldest first); call on the EDT. */
    public List<AgentEvent> eventsFor(String agentId) {
        Deque<AgentEvent> q = events.get(agentId);
        return q == null ? List.of() : new ArrayList<>(q);
    }

    /** Recent posts on a specific group's board (oldest first); call on the EDT. */
    public List<BoardPost> boardPosts(AgentGroup group) {
        Deque<BoardPost> q = boards.get(group.id());
        return q == null ? List.of() : new ArrayList<>(q);
    }

    /** Recent posts across every group board (oldest first per group); call on the EDT. */
    public List<BoardPost> allBoardPosts() {
        List<BoardPost> out = new ArrayList<>();
        for (Deque<BoardPost> q : boards.values()) {
            out.addAll(q);
        }
        return out;
    }

    /** Snapshot of every topic seen so far (insertion order); call on the EDT. */
    public List<String> rawTopics() {
        return new ArrayList<>(rawByTopic.keySet());
    }

    /** Recent raw messages for a topic (oldest first); call on the EDT. */
    public List<RawMessage> rawMessagesFor(String topic) {
        Deque<RawMessage> q = rawByTopic.get(topic);
        return q == null ? List.of() : new ArrayList<>(q);
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
            // Only let a registry payload that actually reported a group override the
            // GUI's current assignment; otherwise carry the existing one forward so a
            // group-less republish doesn't wipe an optimistic (or prior) selection.
            if (agent.isGroupReported()) {
                agentGroups.put(agent.getId(), agent.getGroup());
            } else {
                agent.setGroup(agentGroups.getOrDefault(agent.getId(), AgentGroup.DEFAULT));
            }
            agents.put(agent.getId(), agent);
            listeners.forEach(l -> l.agentsChanged());
            listeners.forEach(l -> l.agentUpdated(agent));
        });
    }

    @Override
    public void onAgentRemoved(String agentId) {
        edt(() -> {
            agentGroups.remove(agentId);
            if (agents.remove(agentId) != null) {
                listeners.forEach(l -> l.agentsChanged());
            }
        });
    }

    @Override
    public void onConsoleUpdated(ConsoleInfo console) {
        edt(() -> {
            consoles.put(console.id(), console);
            listeners.forEach(l -> l.consolesChanged());
        });
    }

    @Override
    public void onConsoleRemoved(String consoleId) {
        edt(() -> {
            if (consoles.remove(consoleId) != null) {
                listeners.forEach(l -> l.consolesChanged());
            }
        });
    }

    @Override
    public void onConsoleReply(JsonNode reply) {
        edt(() -> listeners.forEach(l -> l.consoleReply(reply)));
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        recordEvent(event);
    }

    @Override
    public void onControlReply(AgentEvent event) {
        // Control replies share the per-agent timeline so monitors show a single
        // merged feed of work events and control acks/results.
        recordEvent(event);
    }

    /**
     * Seed per-agent feeds restored from disk at startup. Call before any UI
     * listeners are attached: it populates the history (respecting the configured
     * per-agent cap) without firing events, so monitors replay it on open.
     */
    public void seedEvents(Map<String, List<AgentEvent>> byAgent) {
        edt(() -> {
            int cap = config.getUi().getEventBufferSize();
            for (Map.Entry<String, List<AgentEvent>> e : byAgent.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                Deque<AgentEvent> q = events.computeIfAbsent(e.getKey(), k -> new ArrayDeque<>());
                for (AgentEvent ev : e.getValue()) {
                    q.addLast(ev);
                    while (q.size() > cap) {
                        q.removeFirst();
                    }
                }
            }
        });
    }

    /** Seed the group boards from disk at startup (respects the per-board cap). */
    public void seedBoard(List<BoardPost> posts) {
        edt(() -> {
            int cap = config.getUi().getBoardHistorySize();
            for (BoardPost p : posts) {
                Deque<BoardPost> q = boards.computeIfAbsent(p.groupOrDefault().id(), k -> new ArrayDeque<>());
                q.addLast(p);
                while (q.size() > cap) {
                    q.removeFirst();
                }
            }
        });
    }

    private void recordEvent(AgentEvent event) {
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
    public void onRawMessage(RawMessage message) {
        edt(() -> {
            Deque<RawMessage> q = rawByTopic.computeIfAbsent(message.topic(), k -> new ArrayDeque<>());
            q.addLast(message);
            int cap = config.getUi().getDebugBufferSize();
            while (q.size() > cap) {
                q.removeFirst();
            }
            listeners.forEach(l -> l.rawMessage(message));
        });
    }

    /**
     * Clear the locally-cached history for one group's board and notify
     * listeners so any open board windows wipe their view. This only affects
     * this client's own cache/view (board posts are non-retained, so there is
     * no shared broker state to clear); call on the EDT.
     */
    public void clearBoard(AgentGroup group) {
        edt(() -> {
            Deque<BoardPost> q = boards.get(group.id());
            if (q != null) {
                q.clear();
            }
            listeners.forEach(l -> l.boardCleared(group));
        });
    }

    @Override
    public void onBoardPost(BoardPost post) {
        edt(() -> {
            Deque<BoardPost> q = boards.computeIfAbsent(post.groupOrDefault().id(), k -> new ArrayDeque<>());
            q.addLast(post);
            int cap = config.getUi().getBoardHistorySize();
            while (q.size() > cap) {
                q.removeFirst();
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

        /** A group's board history was cleared locally. */
        default void boardCleared(AgentGroup group) {
        }

        default void rawMessage(RawMessage message) {
        }

        default void consolesChanged() {
        }

        default void consoleReply(JsonNode reply) {
        }
    }

    /** Convenience for callers that only want agent collection snapshots. */
    public Collection<Agent> agents() {
        return agents.values();
    }
}
