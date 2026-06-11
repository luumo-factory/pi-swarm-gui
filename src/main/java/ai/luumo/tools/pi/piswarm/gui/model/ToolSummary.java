package ai.luumo.tools.pi.piswarm.gui.model;

import java.util.List;

/**
 * The agent's tool state from the registry / control replies.
 *
 * @param active    currently active tool names
 * @param available all tool names the agent has registered
 */
public record ToolSummary(List<String> active, List<String> available) {

    public static ToolSummary empty() {
        return new ToolSummary(List.of(), List.of());
    }

    public boolean isActive(String tool) {
        return active.contains(tool);
    }
}
