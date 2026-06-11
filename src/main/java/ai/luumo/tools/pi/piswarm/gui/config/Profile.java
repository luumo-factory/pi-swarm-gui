package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A named launch profile for spawning a new agent through a swarm console.
 *
 * <p>All agent-shaping parameters are optional: an empty profile spawns a
 * default agent. {@code agentName} (when set) becomes the spawned agent's
 * {@code --name}; {@code model} is passed to {@code --model} (e.g.
 * {@code anthropic/claude-sonnet-4-5} or a fuzzy query like {@code sonnet});
 * {@code extensions} are extra {@code --extension} specs/paths;
 * {@code workingDir} is the directory the agent is spawned in (defaults to the
 * user's home directory when left blank).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Profile {

    private String name = "";
    private String agentName = "";
    private String model = "";
    private String workingDir = "";
    private List<String> extensions = new ArrayList<>();

    public Profile() {
    }

    public Profile(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName == null ? "" : agentName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir == null ? "" : workingDir;
    }

    /**
     * The effective working directory for a spawn: the configured
     * {@link #getWorkingDir() workingDir} when set, otherwise the user's home
     * directory.
     */
    public String resolvedWorkingDir() {
        return workingDir == null || workingDir.isBlank()
                ? System.getProperty("user.home", "")
                : workingDir.trim();
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? new ArrayList<>() : extensions;
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? "(unnamed profile)" : name;
    }
}
