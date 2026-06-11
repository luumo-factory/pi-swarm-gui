package ai.luumo.tools.pi.piswarm.gui.history;

import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Persists the scrollback "contents" of the messaging windows between runs so the
 * full feed can be restored on launch:
 *
 * <ul>
 *   <li>one {@code events-&lt;agentId&gt;.json} file per agent monitor, holding its
 *       event/control timeline;</li>
 *   <li>a single {@code board.json} file holding the shared message board.</li>
 * </ul>
 *
 * <p>On {@link #persist} every existing per-agent file is removed first and only
 * the live agents are written back, so files for agents that no longer exist are
 * automatically pruned and the directory cannot grow without bound.</p>
 */
public final class HistoryStore {

    private static final String EVENTS_PREFIX = "events-";
    private static final String EVENTS_SUFFIX = ".json";
    private static final String BOARD_FILE = "board.json";

    private final Path dir;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public HistoryStore(Path dir) {
        this.dir = dir;
    }

    /** Load every saved agent feed, keyed by agent id (oldest event first). */
    public Map<String, List<AgentEvent>> loadEvents() {
        Map<String, List<AgentEvent>> out = new LinkedHashMap<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(HistoryStore::isEventsFile).toList()) {
                try {
                    EventHistory h = mapper.readValue(p.toFile(), EventHistory.class);
                    if (h != null && h.agentId() != null && h.events() != null) {
                        out.put(h.agentId(), h.events());
                    }
                } catch (IOException e) {
                    System.err.println("Could not read agent history " + p + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list history dir " + dir + ": " + e.getMessage());
        }
        return out;
    }

    /** Load the saved board posts (oldest first); empty if none saved. */
    public List<BoardPost> loadBoard() {
        Path p = boardPath();
        if (p == null || !Files.exists(p)) {
            return List.of();
        }
        try {
            BoardPost[] arr = mapper.readValue(p.toFile(), BoardPost[].class);
            return new ArrayList<>(Arrays.asList(arr));
        } catch (IOException e) {
            System.err.println("Could not read board history " + p + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Persist the supplied per-agent feeds and board. Every existing agent feed
     * file is deleted first, so only the agents present in {@code eventsByAgent}
     * (the currently live/known agents) survive — stale files are pruned.
     */
    public void persist(Map<String, List<AgentEvent>> eventsByAgent, List<BoardPost> board) {
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Could not create history dir " + dir + ": " + e.getMessage());
            return;
        }

        deleteAllEventFiles();
        for (Map.Entry<String, List<AgentEvent>> entry : eventsByAgent.entrySet()) {
            List<AgentEvent> events = entry.getValue();
            if (events == null || events.isEmpty()) {
                continue;
            }
            Path p = eventsPath(entry.getKey());
            try {
                mapper.writeValue(p.toFile(), new EventHistory(entry.getKey(), events));
            } catch (IOException e) {
                System.err.println("Could not write agent history " + p + ": " + e.getMessage());
            }
        }

        try {
            Path bp = boardPath();
            if (board == null || board.isEmpty()) {
                Files.deleteIfExists(bp);
            } else {
                mapper.writeValue(bp.toFile(), board);
            }
        } catch (IOException e) {
            System.err.println("Could not write board history: " + e.getMessage());
        }
    }

    private void deleteAllEventFiles() {
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(HistoryStore::isEventsFile).toList()) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static boolean isEventsFile(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith(EVENTS_PREFIX) && name.endsWith(EVENTS_SUFFIX);
    }

    private Path eventsPath(String agentId) {
        return dir.resolve(EVENTS_PREFIX + sanitize(agentId) + EVENTS_SUFFIX);
    }

    private Path boardPath() {
        return dir == null ? null : dir.resolve(BOARD_FILE);
    }

    private static String sanitize(String agentId) {
        return agentId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** On-disk wrapper for one agent's feed (keeps the canonical id with the data). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventHistory(String agentId, List<AgentEvent> events) {
    }
}
