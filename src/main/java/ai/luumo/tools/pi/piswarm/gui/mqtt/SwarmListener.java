package ai.luumo.tools.pi.piswarm.gui.mqtt;

import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;

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

    /** An event arrived on an agent's out stream. */
    default void onAgentEvent(AgentEvent event) {
    }

    /** A new post appeared on the shared board. */
    default void onBoardPost(BoardPost post) {
    }
}
