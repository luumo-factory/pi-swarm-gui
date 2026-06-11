package ai.luumo.tools.pi.piswarm.gui.model;

import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Live view of a single swarm agent, assembled from its retained registry topic.
 */
public final class Agent {

    private final String id;
    private String name;
    private AgentStatus status = AgentStatus.UNKNOWN;
    /** True once a registry payload carrying a recognized status has been seen. */
    private boolean statusKnown;
    private ModelRef model;
    private List<ModelRef> availableModels = new ArrayList<>();
    private List<ExtensionInfo> extensions = new ArrayList<>();
    private ToolSummary tools = ToolSummary.empty();
    private Integer pid;
    private String cwd;
    private long startedAt;
    private long lastSeen;

    public Agent(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name == null ? id : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    /**
     * Whether we have actually received a status update for this agent (as opposed
     * to merely seeing a stale/retained or status-less registry topic).
     */
    public boolean isStatusKnown() {
        return statusKnown;
    }

    public void setStatusKnown(boolean statusKnown) {
        this.statusKnown = statusKnown;
    }

    public ModelRef getModel() {
        return model;
    }

    public void setModel(ModelRef model) {
        this.model = model;
    }

    public List<ModelRef> getAvailableModels() {
        return availableModels;
    }

    public void setAvailableModels(List<ModelRef> availableModels) {
        this.availableModels = availableModels == null ? new ArrayList<>() : availableModels;
    }

    public List<ExtensionInfo> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<ExtensionInfo> extensions) {
        this.extensions = extensions == null ? new ArrayList<>() : extensions;
    }

    public ToolSummary getTools() {
        return tools;
    }

    public void setTools(ToolSummary tools) {
        this.tools = tools == null ? ToolSummary.empty() : tools;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String modelLabel() {
        return model == null ? "no-model" : model.displayLabel();
    }
}
