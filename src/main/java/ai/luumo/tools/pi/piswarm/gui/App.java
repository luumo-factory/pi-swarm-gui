package ai.luumo.tools.pi.piswarm.gui;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.config.ConfigLoader;
import ai.luumo.tools.pi.piswarm.gui.config.ProfilesConfig;
import ai.luumo.tools.pi.piswarm.gui.config.SessionConfig;
import ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmClient;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;
import ai.luumo.tools.pi.piswarm.gui.ui.MainFrame;
import ai.luumo.tools.pi.piswarm.gui.ui.ThemeManager;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * Application entry point for the Pi Swarm monitoring GUI.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        String configArg = args.length > 0 ? args[0] : null;
        Path configPath = ConfigLoader.resolvePath(configArg);
        Path sessionPath = ConfigLoader.sessionPath(configPath);

        AppConfig config;
        try {
            config = ConfigLoader.loadOrCreate(configPath);
        } catch (Exception e) {
            System.err.println("Failed to load config from " + configPath + ": " + e.getMessage());
            config = new AppConfig();
        }
        final AppConfig cfg = config;
        final SessionConfig session = ConfigLoader.loadSession(sessionPath);
        Path profilesPath = ConfigLoader.profilesPath(configPath);
        final ProfilesConfig profiles = ConfigLoader.loadProfiles(profilesPath);

        ThemeManager theme = new ThemeManager();
        theme.apply(ThemeManager.Theme.from(cfg.getUi().getTheme()));

        SwarmModel model = new SwarmModel(cfg);
        SwarmClient client = new SwarmClient(cfg);
        client.addListener(model);

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(cfg, model, client, theme, session, profiles,
                    configPath, sessionPath, profilesPath);
            frame.setVisible(true);

            // Connect off the EDT (so a slow/missing broker never freezes the UI),
            // retrying until the broker is reachable. The status bar reflects the
            // live connection state.
            client.connectWithRetry();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(client::disconnect, "mqtt-shutdown"));
    }
}
