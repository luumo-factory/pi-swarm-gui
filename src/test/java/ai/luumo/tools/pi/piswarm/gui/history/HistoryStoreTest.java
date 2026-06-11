package ai.luumo.tools.pi.piswarm.gui.history;

import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AgentEvent event(String agentId, String type, long ts, String text) {
        ObjectNode raw = MAPPER.createObjectNode();
        raw.put("type", type);
        raw.put("ts", ts);
        if (text != null) {
            raw.put("text", text);
        }
        return new AgentEvent(agentId, type, ts, raw);
    }

    @Test
    void roundTripsEventsAndBoard(@TempDir Path dir) {
        HistoryStore store = new HistoryStore(dir);

        AgentEvent e1 = event("alpha", "agent_start", 1000L, null);
        AgentEvent e2 = event("alpha", "agent_end", 2000L, "done");
        BoardPost post = new BoardPost(1L, "alpha", "Alpha", "hello", true, 1500L, "red");

        store.persist(Map.of("alpha", List.of(e1, e2)), List.of(post));

        Map<String, List<AgentEvent>> events = store.loadEvents();
        assertEquals(List.of("alpha"), List.copyOf(events.keySet()));
        List<AgentEvent> loaded = events.get("alpha");
        assertEquals(2, loaded.size());
        assertEquals("agent_start", loaded.get(0).type());
        assertEquals(2000L, loaded.get(1).ts());
        assertEquals("done", loaded.get(1).text());

        List<BoardPost> board = store.loadBoard();
        assertEquals(1, board.size());
        assertEquals("hello", board.get(0).text());
        assertTrue(board.get(0).urgent());
        assertEquals("red", board.get(0).group());
    }

    @Test
    void prunesFilesForAgentsThatAreNoLongerLive(@TempDir Path dir) throws Exception {
        HistoryStore store = new HistoryStore(dir);

        store.persist(Map.of(
                "alpha", List.of(event("alpha", "agent_start", 1L, null)),
                "bravo", List.of(event("bravo", "agent_start", 1L, null))), List.of());
        assertTrue(Files.exists(dir.resolve("events-alpha.json")));
        assertTrue(Files.exists(dir.resolve("events-bravo.json")));

        // Re-persist with only alpha live: bravo's file must be pruned.
        store.persist(Map.of("alpha", List.of(event("alpha", "agent_end", 2L, "x"))), List.of());

        assertTrue(Files.exists(dir.resolve("events-alpha.json")));
        assertFalse(Files.exists(dir.resolve("events-bravo.json")));
        assertEquals(List.of("alpha"), List.copyOf(store.loadEvents().keySet()));
    }

    @Test
    void handlesEmptyDirectoryGracefully(@TempDir Path dir) {
        HistoryStore store = new HistoryStore(dir.resolve("does-not-exist-yet"));
        assertTrue(store.loadEvents().isEmpty());
        assertTrue(store.loadBoard().isEmpty());
    }
}
