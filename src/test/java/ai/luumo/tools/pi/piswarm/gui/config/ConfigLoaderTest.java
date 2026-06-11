package ai.luumo.tools.pi.piswarm.gui.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void appConfigRoundTripsConnectionSettings(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("app-config.json");
        AppConfig cfg = new AppConfig();
        cfg.getMqtt().setHost("broker.example.com");
        cfg.getMqtt().setPort(8883);
        cfg.getMqtt().setNamespace("fleet");
        cfg.getMqtt().setUsername("alice");
        cfg.getMqtt().setTls(true);
        ConfigLoader.save(path, cfg);

        AppConfig loaded = ConfigLoader.loadOrCreate(path);
        assertEquals("broker.example.com", loaded.getMqtt().getHost());
        assertEquals(8883, loaded.getMqtt().getPort());
        assertEquals("fleet", loaded.getMqtt().getNamespace());
        assertEquals("alice", loaded.getMqtt().getUsername());
        assertTrue(loaded.getMqtt().isTls());
        assertEquals("ssl://broker.example.com:8883", loaded.getMqtt().serverUri());
    }

    @Test
    void migratesLegacyConfigJsonForward(@TempDir Path dir) throws Exception {
        // Pre-rename users have a config.json with their real broker settings.
        Path legacy = dir.resolve("config.json");
        AppConfig original = new AppConfig();
        original.getMqtt().setHost("10.0.0.5");
        original.getMqtt().setPort(1884);
        ConfigLoader.save(legacy, original);

        Path appConfig = dir.resolve("app-config.json");
        AppConfig loaded = ConfigLoader.loadOrCreate(appConfig);

        assertEquals("10.0.0.5", loaded.getMqtt().getHost());
        assertEquals(1884, loaded.getMqtt().getPort());
        assertTrue(Files.exists(appConfig), "legacy config should be migrated to app-config.json");
    }

    @Test
    void existingAppConfigWinsOverLegacy(@TempDir Path dir) throws Exception {
        Path legacy = dir.resolve("config.json");
        AppConfig old = new AppConfig();
        old.getMqtt().setHost("legacy-host");
        ConfigLoader.save(legacy, old);

        Path appConfig = dir.resolve("app-config.json");
        AppConfig current = new AppConfig();
        current.getMqtt().setHost("current-host");
        ConfigLoader.save(appConfig, current);

        AppConfig loaded = ConfigLoader.loadOrCreate(appConfig);
        assertEquals("current-host", loaded.getMqtt().getHost());
    }

    @Test
    void freshInstallWritesLocalhostDefault(@TempDir Path dir) throws Exception {
        Path appConfig = dir.resolve("app-config.json");
        AppConfig loaded = ConfigLoader.loadOrCreate(appConfig);
        assertEquals("127.0.0.1", loaded.getMqtt().getHost());
        assertEquals(1883, loaded.getMqtt().getPort());
        assertTrue(Files.exists(appConfig));
        assertFalse(Files.exists(dir.resolve("config.json")));
    }
}
