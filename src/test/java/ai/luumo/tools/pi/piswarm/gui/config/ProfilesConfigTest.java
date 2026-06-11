package ai.luumo.tools.pi.piswarm.gui.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilesConfigTest {

    @Test
    void resolvesProfilesPathSideBySide(@TempDir Path dir) {
        Path app = dir.resolve("app-config.json");
        assertEquals(dir.resolve("profiles-config.json"), ConfigLoader.profilesPath(app));
    }

    @Test
    void roundTrips(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("profiles-config.json");

        ProfilesConfig cfg = new ProfilesConfig();
        Profile p = new Profile("Reviewer");
        p.setAgentName("reviewer");
        p.setModel("anthropic/claude-sonnet-4-5");
        p.setExtensions(List.of("./review-ext.ts", "npm:foo"));
        cfg.getProfiles().add(p);
        ConfigLoader.saveProfiles(path, cfg);

        ProfilesConfig loaded = ConfigLoader.loadProfiles(path);
        assertEquals(1, loaded.getProfiles().size());
        Profile lp = loaded.getProfiles().get(0);
        assertEquals("Reviewer", lp.getName());
        assertEquals("reviewer", lp.getAgentName());
        assertEquals("anthropic/claude-sonnet-4-5", lp.getModel());
        assertEquals(List.of("./review-ext.ts", "npm:foo"), lp.getExtensions());
    }

    @Test
    void missingFileYieldsEmpty(@TempDir Path dir) {
        ProfilesConfig loaded = ConfigLoader.loadProfiles(dir.resolve("nope.json"));
        assertNotNull(loaded);
        assertTrue(loaded.getProfiles().isEmpty());
    }
}
