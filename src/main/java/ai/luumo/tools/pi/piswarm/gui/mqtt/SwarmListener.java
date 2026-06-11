package ai.luumo.tools.pi.piswarm.gui.mqtt;

import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.model.ConsoleInfo;
import ai.luumo.tools.pi.piswarm.gui.model.RawMessage;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Callbacks fired by {@link SwarmClient}. Implementations should not assume any
 * particular thread; UI implementations marshal onto the EDT themselves.
 */
public interface SwarmListener {

    default void onConnected() {
    }

    default void onConnectionLost(Throwable cause) {
    }

    /** A registry update for an agent (created or changed). */
    default void onAgentUpdated(Agent agent) {
    }

    /** An agent deregistered (its retained registry topic was cleared). */
    default void onAgentRemoved(String agentId) {
    }

    /** An event arrived on an agent's work {@code out} stream. */
    default void onAgentEvent(AgentEvent event) {
    }

    /** A reply arrived on an agent's {@code control/out} stream. */
    default void onControlReply(AgentEvent event) {
    }

    /** A new post appeared on the shared board. */
    default void onBoardPost(BoardPost post) {
    }

    /** A console (spawn host) registration was created or changed. */
    default void onConsoleUpdated(ConsoleInfo console) {
    }

    /** A console deregistered (its retained registry topic was cleared). */
    default void onConsoleRemoved(String consoleId) {
    }

    /** A reply/event arrived on the shared {@code console/out} stream. */
    default void onConsoleReply(JsonNode reply) {
    }

    /**
     * Every raw MQTT message as received, before protocol parsing. Used by the
     * debug view; fired for all subscribed topics regardless of type.
     */
    default void onRawMessage(RawMessage message) {
    }
}
