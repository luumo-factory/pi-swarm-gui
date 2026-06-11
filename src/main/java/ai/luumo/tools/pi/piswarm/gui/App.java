package ai.luumo.tools.pi.piswarm.gui;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.config.ConfigLoader;
import ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmClient;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;
import ai.luumo.tools.pi.piswarm.gui.ui.MainFrame;
import ai.luumo.tools.pi.piswarm.gui.ui.ThemeManager;

import javax.swing.JOptionPane;
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

        AppConfig config;
        try {
            config = ConfigLoader.loadOrCreate(configPath);
        } catch (Exception e) {
            System.err.println("Failed to load config from " + configPath + ": " + e.getMessage());
            config = new AppConfig();
        }
        final AppConfig cfg = config;

        ThemeManager theme = new ThemeManager();
        theme.apply(ThemeManager.Theme.from(cfg.getUi().getTheme()));

        SwarmModel model = new SwarmModel(cfg);
        SwarmClient client = new SwarmClient(cfg);
        client.addListener(model);

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(cfg, model, client, theme);
            frame.setVisible(true);

            // Connect off the EDT so a slow/missing broker doesn't freeze the UI.
            new Thread(() -> {
                try {
                    client.connect();
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                            "Could not connect to MQTT broker at " + cfg.getMqtt().serverUri()
                                    + "\n" + e.getMessage()
                                    + "\n\nThe UI will keep retrying automatically.",
                            "MQTT connection", JOptionPane.WARNING_MESSAGE));
                }
            }, "mqtt-connect").start();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(client::disconnect, "mqtt-shutdown"));
    }
}
