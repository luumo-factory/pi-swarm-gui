package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.AppConfig;
import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.mqtt.SwarmClient;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

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
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyVetoException;
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

    private final JDesktopPane desktop = new JDesktopPane();
    private final JLabel statusBar = new JLabel();
    private final Map<String, AgentControlDialog> controlDialogs = new HashMap<>();
    private JInternalFrame boardFrame;

    public MainFrame(AppConfig config, SwarmModel model, SwarmClient client, ThemeManager theme) {
        super("Pi Swarm Monitor");
        this.config = config;
        this.model = model;
        this.client = client;
        this.theme = theme;

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1200, 760);
        setLocationRelativeTo(null);

        AgentListPanel agentList = new AgentListPanel(model, theme, this);
        agentList.setPreferredSize(new Dimension(260, 0));

        desktop.setBackground(javax.swing.UIManager.getColor("Panel.background"));
        desktop.setDesktopManager(new SnappingDesktopManager());
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
    }

    // ------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
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
        bar.add(view);

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
                + "  ·  " + model.agents().size() + " agent(s)");
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
        boardFrame.setSize(620, 420);
        boardFrame.setLocation(20, 20);
        boardFrame.setVisible(true);
        desktop.add(boardFrame);
        try {
            boardFrame.setSelected(true);
        } catch (PropertyVetoException ignored) {
            // not fatal
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
            f.setBounds(col * w, row * h, w, h);
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
        return models == null ? List.of() : models;
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
    public void openControls(Agent agent) {
        AgentControlDialog existing = controlDialogs.get(agent.getId());
        if (existing != null && existing.isDisplayable()) {
            raise(existing);
            return;
        }
        AgentControlDialog dialog = new AgentControlDialog(this, agent, model, this);
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
        int count = desktop.getAllFrames().length;
        frame.setLocation(40 + (count % 6) * 26, 40 + (count % 6) * 26);
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
}
