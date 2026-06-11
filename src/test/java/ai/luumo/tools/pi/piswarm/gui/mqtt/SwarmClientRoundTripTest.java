package ai.luumo.tools.pi.piswarm.gui.mqtt;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.config.Profile;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.model.ConsoleInfo;
import ai.luumo.tools.pi.piswarm.gui.model.RawMessage;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Round-trips against a local MQTT broker if one is reachable on 127.0.0.1:1883.
 * Skipped (not failed) when no broker is available.
 */
class SwarmClientRoundTripTest {

    private static boolean brokerUp() {
        try (Socket s = new Socket("127.0.0.1", 1883)) {
            return s.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private AppConfig config() {
        AppConfig c = new AppConfig();
        c.getMqtt().setHost("127.0.0.1");
        c.getMqtt().setPort(1883);
        c.getMqtt().setNamespace("swarm-test");
        return c;
    }

    @Test
    void receivesRegistryUpdate() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Agent> received = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onAgentUpdated(Agent agent) {
                received.set(agent);
                latch.countDown();
            }
        });
        client.connect();
        // allow subscription to settle
        TimeUnit.MILLISECONDS.sleep(300);

        publish("swarm-test/registry/coder-1",
                "{\"id\":\"coder-1\",\"name\":\"Coder One\",\"status\":\"busy\","
                        + "\"model\":{\"provider\":\"anthropic\",\"id\":\"claude-sonnet-4-5\",\"name\":\"Sonnet\"},"
                        + "\"availableModels\":[{\"provider\":\"anthropic\",\"id\":\"claude-sonnet-4-5\",\"name\":\"Sonnet\"},"
                        + "{\"provider\":\"openai\",\"id\":\"gpt-4o\",\"name\":\"GPT-4o\"}],"
                        + "\"extensions\":[{\"id\":\"/x/plan-mode\",\"source\":\"extension\",\"tools\":[\"read_board\"],"
                        + "\"commands\":[],\"active\":true}],"
                        + "\"tools\":{\"active\":[\"read\",\"edit\"],\"available\":[\"read\",\"edit\",\"read_board\"]},\"ts\":1}",
                true);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "registry update not received");
        Agent a = received.get();
        assertEquals("coder-1", a.getId());
        assertEquals("Coder One", a.getName());
        assertEquals(AgentStatus.BUSY, a.getStatus());
        assertEquals("Sonnet", a.modelLabel());
        assertEquals(2, a.getAvailableModels().size());
        assertEquals(1, a.getExtensions().size());
        assertEquals("plan-mode", a.getExtensions().get(0).shortName());
        assertTrue(a.getExtensions().get(0).active());
        assertEquals(List.of("read", "edit", "read_board"), a.getTools().available());
        assertTrue(a.getTools().isActive("read"));

        client.disconnect();
        clearRetained("swarm-test/registry/coder-1");
    }

    @Test
    void purgeClearsRetainedRegistry() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch added = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        AtomicReference<String> removedId = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onAgentUpdated(Agent agent) {
                added.countDown();
            }

            @Override
            public void onAgentRemoved(String agentId) {
                removedId.set(agentId);
                removed.countDown();
            }
        });
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        // A stale offline agent left behind on a retained registry topic.
        publish("swarm-test/registry/stale-1",
                "{\"id\":\"stale-1\",\"name\":\"Stale\",\"status\":\"offline\",\"ts\":1}", true);
        assertTrue(added.await(5, TimeUnit.SECONDS), "stale agent not received");

        client.purgeAgent("stale-1");

        assertTrue(removed.await(5, TimeUnit.SECONDS), "purge did not clear the retained registry");
        assertEquals("stale-1", removedId.get());

        // A brand-new subscriber must not see the purged retained message.
        SwarmClient fresh = new SwarmClient(config());
        AtomicReference<Agent> lateJoin = new AtomicReference<>();
        fresh.addListener(new SwarmListener() {
            @Override
            public void onAgentUpdated(Agent agent) {
                if ("stale-1".equals(agent.getId())) {
                    lateJoin.set(agent);
                }
            }
        });
        fresh.connect();
        TimeUnit.MILLISECONDS.sleep(500);
        assertEquals(null, lateJoin.get(), "purged agent still retained for late joiners");
        fresh.disconnect();

        client.disconnect();
    }

    @Test
    void receivesControlReply() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AgentEvent> received = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onControlReply(AgentEvent event) {
                received.set(event);
                latch.countDown();
            }
        });
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        publish("swarm-test/agents/coder-1/control/out",
                "{\"id\":\"coder-1\",\"type\":\"set_model_result\",\"ok\":true,"
                        + "\"model\":{\"provider\":\"openai\",\"id\":\"gpt-4o\",\"name\":\"GPT-4o\"},\"ts\":9}",
                false);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "control reply not received");
        AgentEvent e = received.get();
        assertEquals("coder-1", e.agentId());
        assertEquals("set_model_result", e.type());
        assertTrue(e.raw().get("ok").asBoolean());

        client.disconnect();
    }

    @Test
    void modelReceivesMessagesAfterConnectWithRetry() throws Exception {
        // Full GUI chain: client -> SwarmModel -> EDT listener. Guards against the
        // connectComplete->subscribe deadlock that left the UI empty: subscribing
        // must not block Paho's callback thread, so messages must reach the model.
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmModel model = new SwarmModel(config());
        SwarmClient client = new SwarmClient(config());
        client.addListener(model);

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch gotAgent = new CountDownLatch(1);
        model.addListener(new SwarmModel.SwarmModelListener() {
            @Override
            public void connectionChanged(boolean isConnected) {
                if (isConnected) {
                    connected.countDown();
                }
            }

            @Override
            public void agentsChanged() {
                gotAgent.countDown();
            }
        });

        client.connectWithRetry();
        assertTrue(connected.await(5, TimeUnit.SECONDS), "model never saw the connection");
        // Publish after the connection so it isn't a retained late-join.
        TimeUnit.MILLISECONDS.sleep(300);
        publish("swarm-test/registry/chain-1",
                "{\"id\":\"chain-1\",\"name\":\"Chain\",\"status\":\"idle\",\"ts\":1}", true);

        assertTrue(gotAgent.await(5, TimeUnit.SECONDS), "model never received the agent (subscribe deadlock?)");
        client.disconnect();
        clearRetained("swarm-test/registry/chain-1");
    }

    @Test
    void connectWithRetryConnectsToLiveBroker() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        client.addListener(new SwarmListener() {
            @Override
            public void onConnected() {
                latch.countDown();
            }
        });
        client.connectWithRetry();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "connectWithRetry did not connect");
        assertTrue(client.isConnected());
        client.disconnect();
    }

    @Test
    void connectWithRetryKeepsTryingAndStopsOnDisconnect() throws Exception {
        // Point at a port with no broker: it must not connect, must not throw on
        // the caller thread, and disconnect() must halt the retry loop.
        AppConfig c = new AppConfig();
        c.getMqtt().setHost("127.0.0.1");
        c.getMqtt().setPort(1);
        c.getMqtt().setConnectionTimeoutSeconds(1);
        SwarmClient client = new SwarmClient(c);
        client.connectWithRetry();
        TimeUnit.MILLISECONDS.sleep(500);
        assertFalse(client.isConnected());
        client.disconnect(); // sets closing flag so the retry thread exits
    }

    @Test
    void receivesConsoleRegistration() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConsoleInfo> received = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onConsoleUpdated(ConsoleInfo console) {
                received.set(console);
                latch.countDown();
            }
        });
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        publish("swarm-test/console/registry/host-1",
                "{\"type\":\"console\",\"id\":\"host-1\",\"name\":\"Host One\",\"host\":\"mybox\","
                        + "\"pid\":4242,\"agents\":[{\"id\":\"a\"},{\"id\":\"b\"}],\"ts\":7}",
                true);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "console registration not received");
        ConsoleInfo c = received.get();
        assertEquals("host-1", c.id());
        assertEquals("Host One", c.name());
        assertEquals("mybox", c.host());
        assertEquals(2, c.agentCount());

        client.disconnect();
        clearRetained("swarm-test/console/registry/host-1");
    }

    @Test
    void spawnPublishesTargetedRequest() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RawMessage> received = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onRawMessage(RawMessage message) {
                if (message.topic().equals("swarm-test/console/in")) {
                    received.set(message);
                    latch.countDown();
                }
            }
        });
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        Profile p = new Profile("Reviewer");
        p.setAgentName("reviewer");
        p.setModel("sonnet");
        p.setExtensions(List.of("./review-ext.ts"));
        client.spawnAgent("host-1", p);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "spawn request not observed");
        JsonNode n = new ObjectMapper().readTree(received.get().payload());
        assertEquals("spawn", n.get("action").asText());
        assertEquals("host-1", n.get("console").asText());
        assertEquals("reviewer", n.get("name").asText());
        assertEquals("sonnet", n.get("model").asText());
        assertEquals("./review-ext.ts", n.get("extensions").get(0).asText());

        client.disconnect();
    }

    @Test
    void setGroupPublishesControlAction() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        // The GUI client does not subscribe to control/in, so observe the publish
        // with an independent subscriber on that topic.
        MqttClient sub = new MqttClient("tcp://127.0.0.1:1883",
                "it-sub-" + System.nanoTime(), new MemoryPersistence());
        sub.connect();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        sub.subscribe("swarm-test/agents/coder-1/control/in", (t, m) -> {
            received.set(new String(m.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
        });

        SwarmClient client = new SwarmClient(config());
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        client.setGroup("coder-1", "blue");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "set_group control not observed");
        JsonNode n = new ObjectMapper().readTree(received.get());
        assertEquals("set_group", n.get("action").asText());
        assertEquals("blue", n.get("group").asText());

        client.disconnect();
        sub.disconnect();
        sub.close();
    }

    @Test
    void registryReportsGroup() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Agent> received = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onAgentUpdated(Agent agent) {
                received.set(agent);
                latch.countDown();
            }
        });
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        publish("swarm-test/registry/grouped-1",
                "{\"id\":\"grouped-1\",\"name\":\"Grouped\",\"status\":\"idle\",\"group\":\"purple\",\"ts\":1}",
                true);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "grouped registry not received");
        Agent a = received.get();
        assertEquals(ai.luumo.tools.pi.piswarm.gui.model.AgentGroup.PURPLE, a.getGroup());
        assertTrue(a.isGroupReported());

        client.disconnect();
        clearRetained("swarm-test/registry/grouped-1");
    }

    @Test
    void postToBoardRoundTrips() throws Exception {
        assumeTrue(brokerUp(), "no MQTT broker on 127.0.0.1:1883");

        SwarmClient client = new SwarmClient(config());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BoardPost> received = new AtomicReference<>();
        client.addListener(new SwarmListener() {
            @Override
            public void onBoardPost(BoardPost post) {
                received.set(post);
                latch.countDown();
            }
        });
        client.connect();
        TimeUnit.MILLISECONDS.sleep(300);

        client.postToBoard("renamed app/ to web/", true);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "board post not received");
        BoardPost p = received.get();
        assertEquals("renamed app/ to web/", p.text());
        assertTrue(p.urgent());
        assertEquals("monitor", p.fromName());

        client.disconnect();
    }

    private void publish(String topic, String payload, boolean retain) throws MqttException {
        MqttClient pub = new MqttClient("tcp://127.0.0.1:1883",
                "it-pub-" + System.nanoTime(), new MemoryPersistence());
        pub.connect();
        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(1);
        msg.setRetained(retain);
        pub.publish(topic, msg);
        pub.disconnect();
        pub.close();
    }

    private void clearRetained(String topic) throws MqttException {
        publish(topic, "", true);
    }
}
