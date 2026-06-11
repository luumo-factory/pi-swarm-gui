package ai.luumo.tools.pi.piswarm.gui.mqtt;

/**
 * Builds the MQTT topic strings used by the pi-mqtt-swarm protocol.
 *
 * <p>{@code NS} is the configurable namespace (default {@code swarm}) and
 * {@code ID} is the slugified agent id.</p>
 */
public final class Topics {

    private final String ns;

    public Topics(String namespace) {
        this.ns = (namespace == null || namespace.isBlank()) ? "swarm" : namespace;
    }

    public String namespace() {
        return ns;
    }

    /** Retained registration/overview + last-will for every agent. */
    public String registryWildcard() {
        return ns + "/registry/+";
    }

    public String registry(String id) {
        return ns + "/registry/" + id;
    }

    /** Extract the agent id from a registry topic, or null if it doesn't match. */
    public String agentIdFromRegistry(String topic) {
        String prefix = ns + "/registry/";
        return topic.startsWith(prefix) ? topic.substring(prefix.length()) : null;
    }

    /** Inbound normal work for an agent (queued, delivered when idle). */
    public String agentIn(String id) {
        return ns + "/agents/" + id + "/in";
    }

    /** Inbound urgent work for an agent (delivered immediately). */
    public String agentInterrupt(String id) {
        return ns + "/agents/" + id + "/interrupt";
    }

    /** Control channel for an agent (set_model, stop, reset, reload, ping). */
    public String agentControl(String id) {
        return ns + "/agents/" + id + "/control";
    }

    /** Outbound event stream for all agents. */
    public String agentOutWildcard() {
        return ns + "/agents/+/out";
    }

    public String agentOut(String id) {
        return ns + "/agents/" + id + "/out";
    }

    /** Extract the agent id from an out topic, or null if it doesn't match. */
    public String agentIdFromOut(String topic) {
        String prefix = ns + "/agents/";
        String suffix = "/out";
        if (topic.startsWith(prefix) && topic.endsWith(suffix)) {
            return topic.substring(prefix.length(), topic.length() - suffix.length());
        }
        return null;
    }

    /** Shared broadcast board. */
    public String board() {
        return ns + "/board";
    }
}
