package ai.luumo.tools.pi.piswarm.gui.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionConfigTest {

    @Test
    void resolvesAppAndSessionPathsSideBySide(@TempDir Path dir) {
        Path app = dir.resolve("app-config.json");
        Path session = ConfigLoader.sessionPath(app);
        assertEquals(dir.resolve("session-config.json"), session);
    }

    @Test
    void explicitDirectoryGetsDefaultFileName(@TempDir Path dir) {
        Path resolved = ConfigLoader.resolvePath(dir.toString());
        assertEquals(dir.resolve(ConfigLoader.APP_CONFIG_FILE), resolved);
    }

    @Test
    void sessionRoundTrips(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("session-config.json");

        SessionConfig session = new SessionConfig();
        session.put(SessionConfig.MAIN, SessionConfig.WindowState.of(new Rectangle(10, 20, 800, 600), true, false));
        session.put(SessionConfig.monitor("coder-1"),
                SessionConfig.WindowState.of(new Rectangle(40, 50, 560, 420), false, true));
        ConfigLoader.saveSession(path, session);

        SessionConfig loaded = ConfigLoader.loadSession(path);
        SessionConfig.WindowState main = loaded.get(SessionConfig.MAIN);
        assertNotNull(main);
        assertEquals(new Rectangle(10, 20, 800, 600), main.bounds());
        assertTrue(main.isMaximized());

        SessionConfig.WindowState mon = loaded.get(SessionConfig.monitor("coder-1"));
        assertNotNull(mon);
        assertTrue(mon.isIcon());
        assertTrue(mon.hasSize());
    }

    @Test
    void missingSessionFileYieldsEmptyConfig(@TempDir Path dir) {
        SessionConfig loaded = ConfigLoader.loadSession(dir.resolve("nope.json"));
        assertNotNull(loaded);
        assertTrue(loaded.getWindows().isEmpty());
    }

    @Test
    void appConfigDefaultsAreCreatedAndSaved(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("app-config.json");
        AppConfig created = ConfigLoader.loadOrCreate(path);
        assertNotNull(created);
        assertTrue(path.toFile().exists(), "default app-config.json should be written");
    }
}
