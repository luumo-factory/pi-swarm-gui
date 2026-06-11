package ai.luumo.tools.pi.piswarm.gui.mqtt;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.model.ExtensionInfo;
import ai.luumo.tools.pi.piswarm.gui.model.ToolSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thin wrapper over Eclipse Paho that speaks the pi-mqtt-swarm protocol:
 * subscribes to registry, board and agent out streams, and publishes board
 * posts, inbound work, interrupts and control actions.
 */
public final class SwarmClient implements MqttCallbackExtended {

    private final AppConfig config;
    private final Topics topics;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<SwarmListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong boardSeq = new AtomicLong(0);

    private MqttClient client;

    public SwarmClient(AppConfig config) {
        this.config = config;
        this.topics = new Topics(config.getMqtt().getNamespace());
    }

    public Topics topics() {
        return topics;
    }

    public void addListener(SwarmListener listener) {
        listeners.add(listener);
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    public void connect() throws MqttException {
        AppConfig.Mqtt m = config.getMqtt();
        String clientId = "pi-swarm-gui-" + Long.toHexString(System.nanoTime());
        client = new MqttClient(m.serverUri(), clientId, new MemoryPersistence());
        client.setCallback(this);

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setKeepAliveInterval(m.getKeepAliveSeconds());
        opts.setConnectionTimeout(m.getConnectionTimeoutSeconds());
        if (m.getUsername() != null && !m.getUsername().isBlank()) {
            opts.setUserName(m.getUsername());
            if (m.getPassword() != null) {
                opts.setPassword(m.getPassword().toCharArray());
            }
        }
        client.connect(opts);
    }

    public void disconnect() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException ignored) {
            // best effort
        }
    }

    private void subscribeAll() throws MqttException {
        client.subscribe(topics.registryWildcard(), 1);
        client.subscribe(topics.agentOutWildcard(), 1);
        client.subscribe(topics.agentControlOutWildcard(), 1);
        client.subscribe(topics.board(), 1);
    }

    // ------------------------------------------------------------------
    // Publishing
    // ------------------------------------------------------------------

    private void publish(String topic, Object payload, boolean retain) {
        if (client == null) {
            return;
        }
        try {
            byte[] bytes = (payload instanceof String s)
                    ? s.getBytes(StandardCharsets.UTF_8)
                    : mapper.writeValueAsBytes(payload);
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(1);
            message.setRetained(retain);
            client.publish(topic, message);
        } catch (Exception e) {
            // QoS1 persistent session will catch up; surface to listeners as a lost-ish event is overkill.
            System.err.println("publish failed to " + topic + ": " + e.getMessage());
        }
    }

    /** Post a message to the shared board as the configured orchestrator identity. */
    public void postToBoard(String text, boolean urgent) {
        ObjectNode node = mapper.createObjectNode();
        node.put("seq", boardSeq.incrementAndGet());
        ObjectNode from = node.putObject("from");
        from.put("id", config.getOrchestrator().getId());
        from.put("name", config.getOrchestrator().getName());
        node.put("text", text);
        node.put("urgent", urgent);
        node.put("ts", System.currentTimeMillis());
        publish(topics.board(), node, false);
    }

    /** Send normal (idle-gated) work to an agent. */
    public void sendToAgent(String agentId, String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("text", text);
        publish(topics.agentIn(agentId), node, false);
    }

    /** Send urgent work via the interrupt channel (delivered immediately). */
    public void interruptAgent(String agentId, String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("text", text);
        publish(topics.agentInterrupt(agentId), node, false);
    }

    /**
     * Hard-cancel the agent's current turn via the control plane
     * ({@code {"action":"abort"}}). Distinct from {@link #interruptAgent} which
     * injects a steering message into the running turn.
     */
    public void abortAgent(String agentId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", "abort");
        publish(topics.agentControlIn(agentId), node, false);
    }

    /** Ask the agent to switch model. */
    public void setModel(String agentId, ModelRef model) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", "set_model");
        node.put("provider", model.getProvider());
        node.put("modelId", model.getId());
        publish(topics.agentControlIn(agentId), node, false);
    }

    public void resetAgent(String agentId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", "reset");
        publish(topics.agentControlIn(agentId), node, false);
    }

    public void pingAgent(String agentId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", "ping");
        publish(topics.agentControlIn(agentId), node, false);
    }

    /** Request a full status snapshot on the agent's {@code control/out}. */
    public void requestStatus(String agentId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", "status");
        publish(topics.agentControlIn(agentId), node, false);
    }

    /** Enable or disable an extension (matched by path/basename/source substring). */
    public void setExtensionEnabled(String agentId, String extension, boolean enabled) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", enabled ? "enable_extension" : "disable_extension");
        node.put("extension", extension);
        publish(topics.agentControlIn(agentId), node, false);
    }

    /** Enable or disable specific tools by name. */
    public void setToolsEnabled(String agentId, List<String> tools, boolean enabled) {
        ObjectNode node = mapper.createObjectNode();
        node.put("action", enabled ? "enable_tools" : "disable_tools");
        node.set("tools", mapper.valueToTree(tools));
        publish(topics.agentControlIn(agentId), node, false);
    }

    // ------------------------------------------------------------------
    // MqttCallbackExtended
    // ------------------------------------------------------------------

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            subscribeAll();
        } catch (MqttException e) {
            System.err.println("subscribe failed: " + e.getMessage());
        }
        listeners.forEach(SwarmListener::onConnected);
    }

    @Override
    public void connectionLost(Throwable cause) {
        listeners.forEach(l -> l.onConnectionLost(cause));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

        String registryId = topics.agentIdFromRegistry(topic);
        if (registryId != null) {
            handleRegistry(registryId, payload);
            return;
        }
        String controlId = topics.agentIdFromControlOut(topic);
        if (controlId != null) {
            handleControlOut(controlId, payload);
            return;
        }
        String outId = topics.agentIdFromOut(topic);
        if (outId != null) {
            handleOut(outId, payload);
            return;
        }
        if (topic.equals(topics.board())) {
            handleBoard(payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // not needed
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private void handleRegistry(String id, String payload) {
        if (payload == null || payload.isBlank()) {
            return; // retained tombstone clear
        }
        try {
            JsonNode n = mapper.readTree(payload);
            Agent agent = new Agent(id);
            agent.setName(text(n, "name", id));
            agent.setStatus(AgentStatus.from(text(n, "status", null)));
            agent.setModel(parseModel(n.get("model")));
            agent.setAvailableModels(parseModels(n.get("availableModels")));
            agent.setExtensions(parseExtensions(n.get("extensions")));
            agent.setTools(parseTools(n.get("tools")));
            if (n.hasNonNull("pid")) {
                agent.setPid(n.get("pid").asInt());
            }
            agent.setCwd(text(n, "cwd", null));
            if (n.hasNonNull("startedAt")) {
                agent.setStartedAt(n.get("startedAt").asLong());
            }
            agent.setLastSeen(n.hasNonNull("ts") ? n.get("ts").asLong() : System.currentTimeMillis());
            listeners.forEach(l -> l.onAgentUpdated(agent));
        } catch (Exception e) {
            System.err.println("bad registry payload for " + id + ": " + e.getMessage());
        }
    }

    private void handleOut(String id, String payload) {
        try {
            JsonNode n = mapper.readTree(payload);
            String type = text(n, "type", "event");
            long ts = n.hasNonNull("ts") ? n.get("ts").asLong() : System.currentTimeMillis();
            AgentEvent event = new AgentEvent(id, type, ts, n);
            listeners.forEach(l -> l.onAgentEvent(event));
        } catch (Exception e) {
            System.err.println("bad out payload for " + id + ": " + e.getMessage());
        }
    }

    private void handleControlOut(String id, String payload) {
        try {
            JsonNode n = mapper.readTree(payload);
            String type = text(n, "type", "reply");
            long ts = n.hasNonNull("ts") ? n.get("ts").asLong() : System.currentTimeMillis();
            AgentEvent event = new AgentEvent(id, type, ts, n);
            listeners.forEach(l -> l.onControlReply(event));
        } catch (Exception e) {
            System.err.println("bad control/out payload for " + id + ": " + e.getMessage());
        }
    }

    private void handleBoard(String payload) {
        try {
            JsonNode n = mapper.readTree(payload);
            JsonNode from = n.get("from");
            BoardPost post = new BoardPost(
                    n.hasNonNull("seq") ? n.get("seq").asLong() : 0,
                    from != null ? text(from, "id", "?") : "?",
                    from != null ? text(from, "name", "?") : "?",
                    text(n, "text", ""),
                    n.hasNonNull("urgent") && n.get("urgent").asBoolean(),
                    n.hasNonNull("ts") ? n.get("ts").asLong() : System.currentTimeMillis());
            listeners.forEach(l -> l.onBoardPost(post));
        } catch (Exception e) {
            System.err.println("bad board payload: " + e.getMessage());
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? fallback : v.asText();
    }

    private static ModelRef parseModel(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new ModelRef(text(node, "provider", null), text(node, "id", null), text(node, "name", null));
    }

    private static List<ModelRef> parseModels(JsonNode node) {
        List<ModelRef> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                ModelRef ref = parseModel(item);
                if (ref != null) {
                    out.add(ref);
                }
            }
        }
        return out;
    }

    private static List<ExtensionInfo> parseExtensions(JsonNode node) {
        List<ExtensionInfo> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                out.add(new ExtensionInfo(
                        text(item, "id", "unknown"),
                        text(item, "source", "extension"),
                        text(item, "scope", null),
                        text(item, "origin", null),
                        parseStringArray(item.get("tools")),
                        parseStringArray(item.get("commands")),
                        item.hasNonNull("active") && item.get("active").asBoolean()));
            }
        }
        return out;
    }

    private static ToolSummary parseTools(JsonNode node) {
        if (node == null || node.isNull()) {
            return ToolSummary.empty();
        }
        return new ToolSummary(parseStringArray(node.get("active")), parseStringArray(node.get("available")));
    }

    private static List<String> parseStringArray(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    out.add(item.asText());
                }
            }
        }
        return out;
    }
}
