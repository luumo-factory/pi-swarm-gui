package ai.luumo.tools.pi.piswarm.gui.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A parsed event from an agent's {@code NS/agents/ID/out} stream.
 *
 * <p>The raw JSON node is retained so monitor views can render type-specific
 * details without a class per event kind.</p>
 *
 * @param agentId the agent that emitted the event
 * @param type    the {@code type} field (e.g. agent_start, turn_end, agent_end)
 * @param ts      epoch millis (from payload {@code ts}, else receipt time)
 * @param raw     the full parsed payload
 */
public record AgentEvent(String agentId, String type, long ts, JsonNode raw) {

    public String text() {
        JsonNode n = raw == null ? null : raw.get("text");
        return n == null || n.isNull() ? null : n.asText();
    }
}
