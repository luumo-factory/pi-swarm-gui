package ai.luumo.tools.pi.piswarm.gui.mqtt;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
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
