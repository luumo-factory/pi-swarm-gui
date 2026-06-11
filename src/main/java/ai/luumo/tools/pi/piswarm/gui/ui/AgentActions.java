package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;

/**
 * Actions the UI can perform against an agent. Implemented by the main frame,
 * which translates them into MQTT publishes.
 */
public interface AgentActions {

    /** Hard-stop the agent's current turn (control: {@code stop}). */
    void stop(Agent agent);

    /** Cycle to the next available model for this agent. */
    void toggleModel(Agent agent);

    /** Switch the agent to a specific model. */
    void setModel(Agent agent, ModelRef model);

    /** Reset the agent's conversation context. */
    void reset(Agent agent);

    /** Open (or focus) the per-agent monitor window. */
    void openMonitor(Agent agent);

    /** Send normal (idle-gated) work to the agent. */
    void sendMessage(Agent agent, String text);

    /** Send urgent work to the agent via the interrupt channel. */
    void interrupt(Agent agent, String text);
}
