package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@link AppConfig} and {@link SessionConfig} from JSON files in the config
 * directory, writing out defaults when none exist so the user has something to
 * edit. The app config lives in {@code app-config.json} and the UI session state
 * in {@code session-config.json}.
 */
public final class ConfigLoader {

    public static final String APP_CONFIG_FILE = "app-config.json";
    public static final String SESSION_CONFIG_FILE = "session-config.json";
    public static final String PROFILES_CONFIG_FILE = "profiles-config.json";
    /** Pre-rename app-config filename, migrated forward if found. */
    public static final String LEGACY_CONFIG_FILE = "config.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ConfigLoader() {
    }

    /**
     * Resolve the app config path: explicit argument, else {@code $PISWARM_GUI_CONFIG},
     * else {@code ~/.config/pi-swarm-gui/app-config.json}. If the explicit argument
     * names a directory, the default file name is appended.
     */
    public static Path resolvePath(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            Path p = Path.of(explicit);
            return Files.isDirectory(p) ? p.resolve(APP_CONFIG_FILE) : p;
        }
        String env = System.getenv("PISWARM_GUI_CONFIG");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            return Files.isDirectory(p) ? p.resolve(APP_CONFIG_FILE) : p;
        }
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".config", "pi-swarm-gui", APP_CONFIG_FILE);
    }

    /** Session-config path sitting next to the given app-config path. */
    public static Path sessionPath(Path appConfigPath) {
        return sibling(appConfigPath, SESSION_CONFIG_FILE);
    }

    /** Profiles-config path sitting next to the given app-config path. */
    public static Path profilesPath(Path appConfigPath) {
        return sibling(appConfigPath, PROFILES_CONFIG_FILE);
    }

    private static Path sibling(Path appConfigPath, String fileName) {
        Path parent = appConfigPath.toAbsolutePath().getParent();
        return parent == null ? Path.of(fileName) : parent.resolve(fileName);
    }

    /** Load config from the given path, creating a default file if it is absent. */
    public static AppConfig loadOrCreate(Path path) throws IOException {
        if (Files.exists(path)) {
            return MAPPER.readValue(path.toFile(), AppConfig.class);
        }
        // Backward compatibility: the default file used to be "config.json". If a
        // legacy file sits beside the new app-config.json, adopt it (preserving the
        // user's real broker settings) and migrate it forward instead of writing
        // fresh localhost defaults that would never connect.
        Path legacy = sibling(path, LEGACY_CONFIG_FILE);
        if (legacy != null && !legacy.equals(path) && Files.exists(legacy)) {
            AppConfig migrated = MAPPER.readValue(legacy.toFile(), AppConfig.class);
            save(path, migrated);
            return migrated;
        }
        AppConfig defaults = new AppConfig();
        save(path, defaults);
        return defaults;
    }

    public static void save(Path path, AppConfig config) throws IOException {
        writeJson(path, config);
    }

    /** Load session state, returning an empty {@link SessionConfig} if absent or unreadable. */
    public static SessionConfig loadSession(Path path) {
        if (path != null && Files.exists(path)) {
            try {
                return MAPPER.readValue(path.toFile(), SessionConfig.class);
            } catch (IOException e) {
                System.err.println("Could not read session config " + path + ": " + e.getMessage());
            }
        }
        return new SessionConfig();
    }

    public static void saveSession(Path path, SessionConfig session) throws IOException {
        writeJson(path, session);
    }

    /** Load launch profiles, returning an empty config if absent or unreadable. */
    public static ProfilesConfig loadProfiles(Path path) {
        if (path != null && Files.exists(path)) {
            try {
                return MAPPER.readValue(path.toFile(), ProfilesConfig.class);
            } catch (IOException e) {
                System.err.println("Could not read profiles config " + path + ": " + e.getMessage());
            }
        }
        return new ProfilesConfig();
    }

    public static void saveProfiles(Path path, ProfilesConfig profiles) throws IOException {
        writeJson(path, profiles);
    }

    private static void writeJson(Path path, Object value) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        MAPPER.writeValue(path.toFile(), value);
    }
}
