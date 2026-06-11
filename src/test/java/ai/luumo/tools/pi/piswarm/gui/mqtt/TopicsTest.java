package ai.luumo.tools.pi.piswarm.gui.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TopicsTest {

    private final Topics topics = new Topics("swarm");

    @Test
    void buildsAgentTopics() {
        assertEquals("swarm/registry/coder-1", topics.registry("coder-1"));
        assertEquals("swarm/agents/coder-1/in", topics.agentIn("coder-1"));
        assertEquals("swarm/agents/coder-1/interrupt", topics.agentInterrupt("coder-1"));
        assertEquals("swarm/agents/coder-1/control/in", topics.agentControlIn("coder-1"));
        assertEquals("swarm/agents/coder-1/control/out", topics.agentControlOut("coder-1"));
        assertEquals("swarm/agents/coder-1/out", topics.agentOut("coder-1"));
        assertEquals("swarm/board", topics.board());
    }

    @Test
    void distinguishesWorkOutFromControlOut() {
        assertEquals("reviewer", topics.agentIdFromOut("swarm/agents/reviewer/out"));
        // control/out must NOT be misread as a work-out topic
        assertNull(topics.agentIdFromOut("swarm/agents/reviewer/control/out"));
        assertEquals("reviewer", topics.agentIdFromControlOut("swarm/agents/reviewer/control/out"));
        assertNull(topics.agentIdFromControlOut("swarm/agents/reviewer/out"));
    }

    @Test
    void extractsAgentIdFromRegistry() {
        assertEquals("coder-1", topics.agentIdFromRegistry("swarm/registry/coder-1"));
        assertNull(topics.agentIdFromRegistry("swarm/board"));
    }

    @Test
    void extractsAgentIdFromOut() {
        assertEquals("reviewer", topics.agentIdFromOut("swarm/agents/reviewer/out"));
        assertNull(topics.agentIdFromOut("swarm/agents/reviewer/in"));
    }

    @Test
    void defaultsNamespaceWhenBlank() {
        Topics t = new Topics(" ");
        assertEquals("swarm/board", t.board());
    }

    @Test
    void honoursCustomNamespace() {
        Topics t = new Topics("fleet");
        assertEquals("fleet/registry/a", t.registry("a"));
        assertEquals("a", t.agentIdFromRegistry("fleet/registry/a"));
    }
}
