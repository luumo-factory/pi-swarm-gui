package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration, loaded from JSON.
 *
 * <p>Only the MQTT connection is required today; the type is intentionally open
 * for additional sections to be added later.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AppConfig {

    private Mqtt mqtt = new Mqtt();
    private Ui ui = new Ui();

    /** Identity used when this GUI posts to the shared board / acts as orchestrator. */
    private Orchestrator orchestrator = new Orchestrator();

    /**
     * Optional fallback list of models offered in the model picker when an agent's
     * registry message does not (yet) advertise {@code availableModels}.
     */
    private List<ModelRef> fallbackModels = new ArrayList<>();

    public Mqtt getMqtt() {
        return mqtt;
    }

    public void setMqtt(Mqtt mqtt) {
        this.mqtt = mqtt;
    }

    public Ui getUi() {
        return ui;
    }

    public void setUi(Ui ui) {
        this.ui = ui;
    }

    public Orchestrator getOrchestrator() {
        return orchestrator;
    }

    public void setOrchestrator(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public List<ModelRef> getFallbackModels() {
        return fallbackModels;
    }

    public void setFallbackModels(List<ModelRef> fallbackModels) {
        this.fallbackModels = fallbackModels;
    }

    // ------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Mqtt {
        /** Broker host. */
        private String host = "127.0.0.1";
        /** Broker port. */
        private int port = 1883;
        /** Topic namespace root (matches the extension's PI_SWARM_NS, default "swarm"). */
        private String namespace = "swarm";
        /** Optional credentials; null/blank means anonymous. */
        private String username;
        private String password;
        /** Use TLS (mqtts) instead of plain tcp. */
        private boolean tls = false;
        /** Connect/keep-alive tuning. */
        private int keepAliveSeconds = 30;
        private int connectionTimeoutSeconds = 10;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isTls() {
            return tls;
        }

        public void setTls(boolean tls) {
            this.tls = tls;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }

        /** Build the broker server URI, e.g. {@code tcp://127.0.0.1:1883}. */
        public String serverUri() {
            String scheme = tls ? "ssl" : "tcp";
            return scheme + "://" + host + ":" + port;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Ui {
        /** "dark" or "light". */
        private String theme = "dark";
        /** Max events retained per agent monitor buffer. */
        private int eventBufferSize = 500;
        /** Max board posts retained. */
        private int boardHistorySize = 500;

        public String getTheme() {
            return theme;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }

        public int getEventBufferSize() {
            return eventBufferSize;
        }

        public void setEventBufferSize(int eventBufferSize) {
            this.eventBufferSize = eventBufferSize;
        }

        public int getBoardHistorySize() {
            return boardHistorySize;
        }

        public void setBoardHistorySize(int boardHistorySize) {
            this.boardHistorySize = boardHistorySize;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Orchestrator {
        /** id used in board posts' {@code from.id}. */
        private String id = "gui";
        /** name used in board posts' {@code from.name}. */
        private String name = "monitor";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
