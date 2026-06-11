package ai.luumo.tools.pi.piswarm.gui.model;

/**
 * Agent lifecycle status as published on the registry topic.
 */
public enum AgentStatus {
    ONLINE,
    BUSY,
    IDLE,
    OFFLINE,
    UNKNOWN;

    public static AgentStatus from(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.toLowerCase()) {
            case "online" -> ONLINE;
            case "busy" -> BUSY;
            case "idle" -> IDLE;
            case "offline" -> OFFLINE;
            default -> UNKNOWN;
        };
    }

    /** Whether the agent is actively running a turn. */
    public boolean isBusy() {
        return this == BUSY;
    }

    /** Whether the agent is connected (not offline). */
    public boolean isLive() {
        return this == ONLINE || this == BUSY || this == IDLE;
    }

    public String label() {
        return switch (this) {
            case ONLINE -> "online";
            case BUSY -> "busy";
            case IDLE -> "idle";
            case OFFLINE -> "offline";
            case UNKNOWN -> "unknown";
        };
    }
}
