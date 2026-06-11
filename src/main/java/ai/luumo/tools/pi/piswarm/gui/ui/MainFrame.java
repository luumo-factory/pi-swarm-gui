package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.config.ConfigLoader;
import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.config.Profile;
import ai.luumo.tools.pi.piswarm.gui.config.ProfilesConfig;
import ai.luumo.tools.pi.piswarm.gui.config.SessionConfig;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.ConsoleInfo;
import ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmClient;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;
import com.fasterxml.jackson.databind.JsonNode;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main application window: agent list on the left, a tiling desktop on the right
 * holding the shared board and per-agent monitor windows.
 */
public final class MainFrame extends JFrame implements AgentActions, SwarmModel.SwarmModelListener {

    private final AppConfig config;
    private final SwarmModel model;
    private final SwarmClient client;
    private final ThemeManager theme;
    private final SessionConfig session;
    private final ProfilesConfig profiles;
    private final Path appConfigPath;
    private final Path sessionPath;
    private final Path profilesPath;
    private Rectangle mainNormalBounds;
    private ProfileManagerDialog profileManager;
    /**
     * Window ids saved as open last session that we still need to re-open. Monitor
     * and controls windows can only be restored once their agent is discovered
     * over MQTT, so we defer those until the agent appears.
     */
    private final java.util.Set<String> pendingWindowRestores = new java.util.HashSet<>();

    private final JDesktopPane desktop = new JDesktopPane();
    private final SnappingDesktopManager desktopManager = new SnappingDesktopManager();
    private int prevDesktopW;
    private int prevDesktopH;
    private final JLabel statusBar = new JLabel();
    private final Map<String, AgentControlDialog> controlDialogs = new HashMap<>();
    private JInternalFrame boardFrame;
    private JInternalFrame debugFrame;
    private DebugPanel debugPanel;
    private JCheckBoxMenuItem debugMenuItem;

    public MainFrame(AppConfig config, SwarmModel model, SwarmClient client, ThemeManager theme,
                     SessionConfig session, ProfilesConfig profiles,
                     Path appConfigPath, Path sessionPath, Path profilesPath) {
        super("Pi Swarm Monitor");
        this.config = config;
        this.model = model;
        this.client = client;
        this.theme = theme;
        this.session = session;
        this.profiles = profiles;
        this.appConfigPath = appConfigPath;
        this.sessionPath = sessionPath;
        this.profilesPath = profilesPath;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1200, 760);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performExit();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rememberMainNormalBounds();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                rememberMainNormalBounds();
            }
        });

        AgentListPanel agentList = new AgentListPanel(model, theme, this);
        agentList.setPreferredSize(new Dimension(260, 0));

        desktop.setBackground(javax.swing.UIManager.getColor("Panel.background"));
        desktop.setDesktopManager(desktopManager);
        desktop.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = desktop.getWidth();
                int h = desktop.getHeight();
                if (prevDesktopW > 0 && prevDesktopH > 0 && (w != prevDesktopW || h != prevDesktopH)) {
                    desktopManager.desktopResized(desktop, prevDesktopW, prevDesktopH, w, h);
                }
                prevDesktopW = w;
                prevDesktopH = h;
            }
        });
        openBoard();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, agentList, desktop);
        split.setDividerLocation(260);
        split.setOneTouchExpandable(true);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setJMenuBar(buildMenuBar());

        model.addListener(this);
        updateStatusBar();
        restoreMainState();
        restoreOpenWindows();
    }

    /**
     * Re-open windows that were open last session. The debug window can open
     * immediately; monitor/controls windows are queued until their agent is seen
     * over MQTT (see {@link #agentUpdated}).
     */
    private void restoreOpenWindows() {
        for (String id : session.getOpenWindows()) {
            if (SessionConfig.DEBUG.equals(id)) {
                openDebug();
            } else if (id.startsWith("monitor:") || id.startsWith("controls:")) {
                pendingWindowRestores.add(id);
                // If the agent is already known (retained registry delivered fast),
                // restore right away.
                Agent agent = agentForWindowId(id);
                if (agent != null) {
                    restorePendingFor(agent);
                }
            }
        }
    }

    private Agent agentForWindowId(String windowId) {
        int colon = windowId.indexOf(':');
        return colon < 0 ? null : model.agent(windowId.substring(colon + 1));
    }

    /** Open any queued monitor/controls windows for a now-known agent. */
    private void restorePendingFor(Agent agent) {
        if (pendingWindowRestores.remove(SessionConfig.monitor(agent.getId()))) {
            openMonitor(agent);
        }
        if (pendingWindowRestores.remove(SessionConfig.controls(agent.getId()))) {
            openControls(agent);
        }
    }

    // ------------------------------------------------------------------
    // Launching agents / profiles
    // ------------------------------------------------------------------

    @Override
    public List<Profile> launchProfiles() {
        return profiles.getProfiles();
    }

    @Override
    public void launchAgent(Profile profile) {
        List<ConsoleInfo> consoles = model.consolesSorted();
        if (consoles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No swarm console (spawn host) is currently running.\n\n"
                            + "Start one with:  node src/console.ts --name host-1",
                    "Launch agent", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ConsoleInfo target;
        if (consoles.size() == 1) {
            target = consoles.get(0);
        } else {
            target = (ConsoleInfo) JOptionPane.showInputDialog(this,
                    "Select the host to launch the new agent on:", "Launch agent",
                    JOptionPane.QUESTION_MESSAGE, null,
                    consoles.toArray(), consoles.get(0));
            if (target == null) {
                return; // cancelled
            }
        }
        client.spawnAgent(target.id(), profile);
        String label = profile == null ? "<defaults>" : profile.toString();
        statusBar.setText("launch requested (" + label + ") on " + target.name() + " \u2026");
    }

    @Override
    public void openProfileManager() {
        if (profileManager != null && profileManager.isDisplayable()) {
            raise(profileManager);
            return;
        }
        profileManager = new ProfileManagerDialog(this, profiles, this::persistProfiles);
        profileManager.setVisible(true);
        raise(profileManager);
    }

    private void persistProfiles() {
        try {
            ConfigLoader.saveProfiles(profilesPath, profiles);
        } catch (IOException e) {
            System.err.println("Failed to save profiles to " + profilesPath + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Session persistence
    // ------------------------------------------------------------------

    private void rememberMainNormalBounds() {
        if ((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            mainNormalBounds = getBounds();
        }
    }

    private void restoreMainState() {
        SessionConfig.WindowState s = session.get(SessionConfig.MAIN);
        if (s != null && s.hasSize()) {
            setBounds(s.bounds());
            mainNormalBounds = s.bounds();
            if (s.isMaximized()) {
                setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
            }
        }
    }

    /** Apply a saved geometry to an internal frame, falling back to a default rectangle. */
    private void restoreInternal(JInternalFrame frame, String id, Rectangle fallback) {
        SessionConfig.WindowState s = session.get(id);
        if (s != null && s.hasSize()) {
            frame.setBounds(s.bounds());
            try {
                if (s.isMaximized()) {
                    frame.setMaximum(true);
                }
                if (s.isIcon()) {
                    frame.setIcon(true);
                }
            } catch (PropertyVetoException ignored) {
                // not fatal
            }
        } else if (fallback != null) {
            frame.setBounds(fallback);
        }
    }

    private String windowId(JInternalFrame f) {
        if (f instanceof AgentMonitorFrame m) {
            return SessionConfig.monitor(m.getAgentId());
        }
        if (f == boardFrame) {
            return SessionConfig.BOARD;
        }
        if (f == debugFrame) {
            return SessionConfig.DEBUG;
        }
        return null;
    }

    /** Snapshot the geometry of every open window into the session config. */
    private void captureSession() {
        boolean maxed = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        Rectangle mainBounds = maxed && mainNormalBounds != null ? mainNormalBounds : getBounds();
        session.put(SessionConfig.MAIN, SessionConfig.WindowState.of(mainBounds, maxed, false));

        for (JInternalFrame f : desktop.getAllFrames()) {
            if (f.isClosed()) {
                continue;
            }
            String id = windowId(f);
            if (id == null) {
                continue;
            }
            Rectangle b = f.getNormalBounds() != null ? f.getNormalBounds() : f.getBounds();
            session.put(id, SessionConfig.WindowState.of(b, f.isMaximum(), f.isIcon()));
        }

        for (Map.Entry<String, AgentControlDialog> e : controlDialogs.entrySet()) {
            AgentControlDialog dialog = e.getValue();
            if (dialog != null && dialog.isDisplayable()) {
                session.put(SessionConfig.controls(e.getKey()),
                        SessionConfig.WindowState.of(dialog.getBounds(), false, false));
            }
        }

        session.setOpenWindows(currentOpenWindowIds());

        // Persist the running theme so the saved app config reflects runtime choices.
        config.getUi().setTheme(theme.isDark() ? "dark" : "light");
    }

    /** Ids of the windows currently open, for restoring on next launch. */
    private java.util.List<String> currentOpenWindowIds() {
        java.util.List<String> open = new java.util.ArrayList<>();
        for (JInternalFrame f : desktop.getAllFrames()) {
            if (f.isClosed()) {
                continue;
            }
            String id = windowId(f);
            if (id != null) {
                open.add(id);
            }
        }
        for (Map.Entry<String, AgentControlDialog> e : controlDialogs.entrySet()) {
            AgentControlDialog dialog = e.getValue();
            if (dialog != null && dialog.isDisplayable()) {
                open.add(SessionConfig.controls(e.getKey()));
            }
        }
        return open;
    }

    private void persistConfigs() {
        try {
            ConfigLoader.save(appConfigPath, config);
        } catch (IOException e) {
            System.err.println("Failed to save app config to " + appConfigPath + ": " + e.getMessage());
        }
        try {
            ConfigLoader.saveSession(sessionPath, session);
        } catch (IOException e) {
            System.err.println("Failed to save session config to " + sessionPath + ": " + e.getMessage());
        }
        persistProfiles();
    }

    private void performExit() {
        captureSession();
        persistConfigs();
        client.disconnect();
        dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> performExit());
        file.add(exit);
        bar.add(file);

        JMenu view = new JMenu("View");
        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup group = new ButtonGroup();
        JRadioButtonMenuItem dark = new JRadioButtonMenuItem("Dark", theme.isDark());
        JRadioButtonMenuItem light = new JRadioButtonMenuItem("Light", !theme.isDark());
        dark.addActionListener(e -> theme.apply(ThemeManager.Theme.DARK));
        light.addActionListener(e -> theme.apply(ThemeManager.Theme.LIGHT));
        group.add(dark);
        group.add(light);
        themeMenu.add(dark);
        themeMenu.add(light);
        view.add(themeMenu);
        view.addSeparator();
        debugMenuItem = new JCheckBoxMenuItem("Debug (MQTT messages)", false);
        debugMenuItem.addActionListener(e -> {
            if (debugMenuItem.isSelected()) {
                openDebug();
            } else {
                closeDebug();
            }
        });
        view.add(debugMenuItem);
        bar.add(view);

        JMenu swarm = new JMenu("Swarm");
        JMenu launchMenu = new JMenu("Launch new agent");
        JMenuItem launchDefaults = new JMenuItem("<defaults>");
        launchDefaults.addActionListener(e -> launchAgent(null));
        launchMenu.add(launchDefaults);
        // Rebuild the profile entries each time the menu opens so it tracks edits.
        launchMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                launchMenu.removeAll();
                launchMenu.add(launchDefaults);
                for (Profile p : profiles.getProfiles()) {
                    JMenuItem item = new JMenuItem(p.toString());
                    item.addActionListener(ev -> launchAgent(p));
                    launchMenu.add(item);
                }
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        swarm.add(launchMenu);
        JMenuItem manageProfiles = new JMenuItem("Manage profiles…");
        manageProfiles.addActionListener(e -> openProfileManager());
        swarm.add(manageProfiles);
        bar.add(swarm);

        JMenu window = new JMenu("Window");
        JMenuItem tile = new JMenuItem("Tile");
        tile.addActionListener(e -> tileFrames());
        JMenuItem cascade = new JMenuItem("Cascade");
        cascade.addActionListener(e -> cascadeFrames());
        JCheckBoxMenuItem showBoard = new JCheckBoxMenuItem("Show board", true);
        showBoard.addActionListener(e -> {
            if (showBoard.isSelected()) {
                openBoard();
            } else if (boardFrame != null) {
                boardFrame.dispose();
            }
        });
        window.add(tile);
        window.add(cascade);
        window.addSeparator();
        window.add(showBoard);
        bar.add(window);

        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        panel.add(statusBar, BorderLayout.WEST);
        return panel;
    }

    private void updateStatusBar() {
        AppConfig.Mqtt m = config.getMqtt();
        String conn = model.isConnected() ? "connected" : "disconnected";
        statusBar.setText(conn + "  ·  broker " + m.serverUri()
                + "  ·  ns " + m.getNamespace()
                + "  ·  " + model.agentsSorted().size() + " agent(s)"
                + "  ·  " + model.consolesSorted().size() + " host(s)");
    }

    // ------------------------------------------------------------------
    // Internal frame management
    // ------------------------------------------------------------------

    private void openBoard() {
        if (boardFrame != null && !boardFrame.isClosed()) {
            boardFrame.toFront();
            return;
        }
        BoardPanel panel = new BoardPanel(model, theme, client::postToBoard);
        boardFrame = new JInternalFrame("Message board", true, true, true, true);
        boardFrame.setFrameIcon(null);
        boardFrame.setContentPane(panel);
        restoreInternal(boardFrame, SessionConfig.BOARD, new Rectangle(20, 20, 620, 420));
        boardFrame.setVisible(true);
        desktop.add(boardFrame);
        try {
            boardFrame.setSelected(true);
        } catch (PropertyVetoException ignored) {
            // not fatal
        }
    }

    private void openDebug() {
        if (debugFrame != null && !debugFrame.isClosed()) {
            debugFrame.toFront();
            return;
        }
        debugPanel = new DebugPanel(model);
        debugFrame = new JInternalFrame("MQTT debug", true, true, true, true);
        debugFrame.setFrameIcon(null);
        debugFrame.setContentPane(debugPanel);
        restoreInternal(debugFrame, SessionConfig.DEBUG, new Rectangle(60, 60, 820, 480));
        debugFrame.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                if (debugPanel != null) {
                    debugPanel.dispose();
                    debugPanel = null;
                }
                debugFrame = null;
                if (debugMenuItem != null) {
                    debugMenuItem.setSelected(false);
                }
            }
        });
        debugFrame.setVisible(true);
        desktop.add(debugFrame);
        if (debugMenuItem != null) {
            debugMenuItem.setSelected(true);
        }
        try {
            debugFrame.setSelected(true);
        } catch (PropertyVetoException ignored) {
            // not fatal
        }
    }

    private void closeDebug() {
        if (debugFrame != null) {
            debugFrame.dispose();
        }
    }

    private AgentMonitorFrame findMonitor(String agentId) {
        for (JInternalFrame f : desktop.getAllFrames()) {
            if (f instanceof AgentMonitorFrame m && m.getAgentId().equals(agentId)) {
                return m;
            }
        }
        return null;
    }

    private void tileFrames() {
        JInternalFrame[] frames = desktop.getAllFrames();
        int n = frames.length;
        if (n == 0) {
            return;
        }
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / cols);
        int w = desktop.getWidth() / cols;
        int h = desktop.getHeight() / rows;
        for (int i = 0; i < n; i++) {
            JInternalFrame f = frames[i];
            try {
                f.setIcon(false);
                f.setMaximum(false);
            } catch (PropertyVetoException ignored) {
                // ignore
            }
            int col = i % cols;
            int row = i / cols;
            // Overlap neighbours by the border width so tiled frames share a
            // single seam line rather than doubling their 1px borders.
            int cw = w + (col < cols - 1 ? SnappingDesktopManager.BORDER_OVERLAP : 0);
            int ch = h + (row < rows - 1 ? SnappingDesktopManager.BORDER_OVERLAP : 0);
            f.setBounds(col * w, row * h, cw, ch);
        }
    }

    private void cascadeFrames() {
        JInternalFrame[] frames = desktop.getAllFrames();
        int offset = 28;
        int w = Math.max(420, desktop.getWidth() - frames.length * offset - 40);
        int h = Math.max(300, desktop.getHeight() - frames.length * offset - 40);
        for (int i = 0; i < frames.length; i++) {
            JInternalFrame f = frames[i];
            try {
                f.setIcon(false);
                f.setMaximum(false);
            } catch (PropertyVetoException ignored) {
                // ignore
            }
            f.setBounds(20 + i * offset, 20 + i * offset, w, h);
            f.toFront();
        }
    }

    // ------------------------------------------------------------------
    // AgentActions
    // ------------------------------------------------------------------

    @Override
    public void stop(Agent agent) {
        client.abortAgent(agent.getId());
    }

    @Override
    public List<ModelRef> selectableModels(Agent agent) {
        List<ModelRef> models = agent.getAvailableModels();
        if (models == null || models.isEmpty()) {
            models = config.getFallbackModels();
        }
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<ModelRef> sorted = new java.util.ArrayList<>(models);
        sorted.sort(ModelRef.BY_PROVIDER_THEN_MODEL);
        return sorted;
    }

    @Override
    public void setModel(Agent agent, ModelRef model) {
        client.setModel(agent.getId(), model);
    }

    @Override
    public void reset(Agent agent) {
        client.resetAgent(agent.getId());
    }

    @Override
    public void quit(Agent agent) {
        client.quitAgent(agent.getId());
    }

    @Override
    public void sendMessage(Agent agent, String text) {
        client.sendToAgent(agent.getId(), text);
    }

    @Override
    public void interrupt(Agent agent, String text) {
        client.interruptAgent(agent.getId(), text);
    }

    @Override
    public void setExtensionEnabled(Agent agent, String extensionId, boolean enabled) {
        client.setExtensionEnabled(agent.getId(), extensionId, enabled);
    }

    @Override
    public void setToolsEnabled(Agent agent, java.util.List<String> tools, boolean enabled) {
        client.setToolsEnabled(agent.getId(), tools, enabled);
    }

    @Override
    public void requestStatus(Agent agent) {
        client.requestStatus(agent.getId());
    }

    @Override
    public void rename(Agent agent, String newName) {
        // reslug=false keeps the agent id/topics stable so open monitors keep working.
        client.renameAgent(agent.getId(), newName, false);
    }

    @Override
    public void openControls(Agent agent) {
        AgentControlDialog existing = controlDialogs.get(agent.getId());
        if (existing != null && existing.isDisplayable()) {
            raise(existing);
            return;
        }
        AgentControlDialog dialog = new AgentControlDialog(this, agent, model, this);
        SessionConfig.WindowState saved = session.get(SessionConfig.controls(agent.getId()));
        if (saved != null && saved.hasSize()) {
            dialog.setBounds(saved.bounds());
        }
        controlDialogs.put(agent.getId(), dialog);
        dialog.setVisible(true);
        raise(dialog);
    }

    /** Reliably bring a (possibly already-open, non-modal) dialog to the front. */
    private static void raise(JDialog dialog) {
        if (!dialog.isVisible()) {
            dialog.setVisible(true);
        }
        dialog.toFront();
        dialog.requestFocus();
        // Some window managers won't raise a non-modal dialog on toFront() alone;
        // a brief always-on-top toggle forces it above the main window.
        boolean wasOnTop = dialog.isAlwaysOnTop();
        dialog.setAlwaysOnTop(true);
        dialog.setAlwaysOnTop(wasOnTop);
    }

    @Override
    public void openMonitor(Agent agent) {
        AgentMonitorFrame existing = findMonitor(agent.getId());
        if (existing != null) {
            existing.toFront();
            try {
                existing.setSelected(true);
            } catch (PropertyVetoException ignored) {
                // ignore
            }
            return;
        }
        AgentMonitorFrame frame = new AgentMonitorFrame(agent, model, theme, this);
        SessionConfig.WindowState saved = session.get(SessionConfig.monitor(agent.getId()));
        if (saved != null && saved.hasSize()) {
            restoreInternal(frame, SessionConfig.monitor(agent.getId()), null);
        } else {
            int count = desktop.getAllFrames().length;
            frame.setLocation(40 + (count % 6) * 26, 40 + (count % 6) * 26);
        }
        frame.setVisible(true);
        desktop.add(frame);
        try {
            frame.setSelected(true);
        } catch (PropertyVetoException ignored) {
            // ignore
        }
    }

    // ------------------------------------------------------------------
    // SwarmModel listener
    // ------------------------------------------------------------------

    @Override
    public void connectionChanged(boolean connected) {
        updateStatusBar();
    }

    @Override
    public void agentsChanged() {
        updateStatusBar();
    }

    @Override
    public void agentUpdated(Agent agent) {
        // An agent we saw open last session has just been (re)discovered over MQTT:
        // re-open its monitor/controls window now that we know it exists.
        if (!pendingWindowRestores.isEmpty()) {
            restorePendingFor(agent);
        }
    }

    @Override
    public void consolesChanged() {
        updateStatusBar();
    }

    @Override
    public void consoleReply(JsonNode reply) {
        String type = reply.hasNonNull("type") ? reply.get("type").asText() : "";
        boolean ok = !reply.has("ok") || reply.get("ok").asBoolean();
        if ("spawn_result".equals(type)) {
            if (ok) {
                JsonNode agent = reply.get("agent");
                String name = agent != null && agent.hasNonNull("name") ? agent.get("name").asText() : "agent";
                statusBar.setText("spawned " + name);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Spawn failed: " + (reply.hasNonNull("error") ? reply.get("error").asText() : "unknown error"),
                        "Launch agent", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
