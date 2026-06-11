package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@link AppConfig} from a JSON file, writing out a default file when none
 * exists so the user has something to edit.
 */
public final class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ConfigLoader() {
    }

    /**
     * Resolve the config path: explicit argument, else {@code $PISWARM_GUI_CONFIG},
     * else {@code ~/.config/pi-swarm-gui/config.json}.
     */
    public static Path resolvePath(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        String env = System.getenv("PISWARM_GUI_CONFIG");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".config", "pi-swarm-gui", "config.json");
    }

    /** Load config from the given path, creating a default file if it is absent. */
    public static AppConfig loadOrCreate(Path path) throws IOException {
        if (Files.exists(path)) {
            return MAPPER.readValue(path.toFile(), AppConfig.class);
        }
        AppConfig defaults = new AppConfig();
        save(path, defaults);
        return defaults;
    }

    public static void save(Path path, AppConfig config) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        MAPPER.writeValue(path.toFile(), config);
    }
}
