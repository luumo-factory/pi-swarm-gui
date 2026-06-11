package ai.luumo.tools.pi.piswarm.gui.swarm;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentGroup;
import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
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

    private static Agent agentWithGroup(String id, AgentGroup group, boolean reported) {
        Agent a = agent(id, id, AgentStatus.IDLE, true);
        a.setGroup(group);
        a.setGroupReported(reported);
        return a;
    }

    @Test
    void tracksAgentGroupsWithOptimisticOverride() throws Exception {
        SwarmModel model = new SwarmModel(new AppConfig());

        SwingUtilities.invokeAndWait(() -> {
            // No group ever reported -> default red.
            model.onAgentUpdated(agent("a", "Alpha", AgentStatus.IDLE, true));
            // Registry reports blue -> authoritative.
            model.onAgentUpdated(agentWithGroup("b", AgentGroup.BLUE, true));
        });
        assertEquals(AgentGroup.RED, onEdt(() -> model.groupOf("a")));
        assertEquals(AgentGroup.BLUE, onEdt(() -> model.groupOf("b")));

        // User optimistically moves alpha to green; a group-less registry republish
        // must not wipe that selection.
        SwingUtilities.invokeAndWait(() -> {
            model.setAgentGroup("a", AgentGroup.GREEN);
            model.onAgentUpdated(agent("a", "Alpha", AgentStatus.BUSY, true));
        });
        assertEquals(AgentGroup.GREEN, onEdt(() -> model.groupOf("a")));
        // A registry that DOES report a group overrides the optimistic value.
        SwingUtilities.invokeAndWait(() -> model.onAgentUpdated(agentWithGroup("a", AgentGroup.PINK, true)));
        assertEquals(AgentGroup.PINK, onEdt(() -> model.groupOf("a")));
    }

    @Test
    void cachesBoardPostsPerGroup() throws Exception {
        SwarmModel model = new SwarmModel(new AppConfig());

        SwingUtilities.invokeAndWait(() -> {
            model.onBoardPost(new BoardPost(1L, "a", "Alpha", "red-1", false, 10L, "red"));
            model.onBoardPost(new BoardPost(2L, "b", "Bravo", "blue-1", false, 20L, "blue"));
            model.onBoardPost(new BoardPost(3L, "a", "Alpha", "red-2", false, 30L, "red"));
            // Unknown/blank group resolves to the default (red) board.
            model.onBoardPost(new BoardPost(4L, "c", "Charlie", "red-3", false, 40L, null));
        });

        List<String> red = onEdt(() ->
                model.boardPosts(AgentGroup.RED).stream().map(BoardPost::text).toList());
        List<String> blue = onEdt(() ->
                model.boardPosts(AgentGroup.BLUE).stream().map(BoardPost::text).toList());
        List<String> green = onEdt(() ->
                model.boardPosts(AgentGroup.GREEN).stream().map(BoardPost::text).toList());

        assertEquals(List.of("red-1", "red-2", "red-3"), red);
        assertEquals(List.of("blue-1"), blue);
        assertEquals(List.of(), green);
        assertEquals(4, onEdt(() -> model.allBoardPosts().size()));
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
