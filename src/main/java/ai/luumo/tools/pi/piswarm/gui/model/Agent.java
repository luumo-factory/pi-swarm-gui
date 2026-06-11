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
    private ModelRef model;
    private List<ModelRef> availableModels = new ArrayList<>();
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
