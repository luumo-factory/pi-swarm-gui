package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;

import java.util.List;

/**
 * Actions the UI can perform against an agent. Implemented by the main frame,
 * which translates them into MQTT publishes.
 */
public interface AgentActions {

    /** Hard-cancel the agent's current turn (control: {@code abort}). */
    void stop(Agent agent);

    /**
     * The models the user may switch this agent to: the agent's advertised
     * available models, or the configured fallback list when none are reported.
     */
    List<ModelRef> selectableModels(Agent agent);

    /** Switch the agent to a specific model. */
    void setModel(Agent agent, ModelRef model);

    /** Reset the agent's conversation context. */
    void reset(Agent agent);

    /** Open (or focus) the per-agent monitor window. */
    void openMonitor(Agent agent);

    /** Open the per-agent control/details dialog (model, extensions, tools). */
    void openControls(Agent agent);

    /** Send normal (idle-gated) work to the agent. */
    void sendMessage(Agent agent, String text);

    /** Send urgent work to the agent via the interrupt channel. */
    void interrupt(Agent agent, String text);

    /** Enable/disable an extension (by id/path/basename) via the control plane. */
    void setExtensionEnabled(Agent agent, String extensionId, boolean enabled);

    /** Enable/disable specific tools by name via the control plane. */
    void setToolsEnabled(Agent agent, List<String> tools, boolean enabled);

    /** Request a fresh status snapshot on the agent's control/out. */
    void requestStatus(Agent agent);
}
