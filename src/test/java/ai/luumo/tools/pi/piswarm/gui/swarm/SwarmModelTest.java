package ai.luumo.tools.pi.piswarm.gui.swarm;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmModelTest {

    private static Agent agent(String id, String name, AgentStatus status, boolean known) {
        Agent a = new Agent(id);
        a.setName(name);
        a.setStatus(status);
        a.setStatusKnown(known);
        return a;
    }

    @Test
    void hidesStatuslessAndSortsOfflineLast() throws Exception {
        SwarmModel model = new SwarmModel(new AppConfig());

        SwingUtilities.invokeAndWait(() -> {
            model.onAgentUpdated(agent("b", "Bravo", AgentStatus.IDLE, true));
            model.onAgentUpdated(agent("a", "Alpha", AgentStatus.OFFLINE, true));
            model.onAgentUpdated(agent("z", "Zulu", AgentStatus.BUSY, true));
            model.onAgentUpdated(agent("s", "Stale", AgentStatus.UNKNOWN, false));
        });

        List<String> names = onEdt(() ->
                model.agentsSorted().stream().map(Agent::getName).toList());

        // Stale (no status update) is excluded; live agents first by name; offline last.
        assertEquals(List.of("Bravo", "Zulu", "Alpha"), names);
    }

    @Test
    void removesAgentOnTombstone() throws Exception {
        SwarmModel model = new SwarmModel(new AppConfig());

        SwingUtilities.invokeAndWait(() -> {
            model.onAgentUpdated(agent("a", "Alpha", AgentStatus.IDLE, true));
            model.onAgentUpdated(agent("b", "Bravo", AgentStatus.IDLE, true));
            model.onAgentRemoved("a");
        });

        List<String> names = onEdt(() ->
                model.agentsSorted().stream().map(Agent::getName).toList());
        assertEquals(List.of("Bravo"), names);
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> c) throws Exception {
        Object[] box = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                box[0] = c.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        @SuppressWarnings("unchecked")
        T result = (T) box[0];
        return result;
    }
}
