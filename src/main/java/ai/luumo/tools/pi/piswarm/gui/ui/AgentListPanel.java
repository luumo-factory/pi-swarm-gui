package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.config.Profile;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentGroup;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Left-hand panel split into three sections:
 * <ol>
 *   <li><b>Named Agents</b> — manually-launched agents, with the launch button
 *       at the top. Double-click opens a monitor; right-click exposes the agent
 *       actions (stop / model / reset / rename / quit / purge).</li>
 *   <li><b>Auto Agents</b> — reserved for auto-spawned agents (empty for now).</li>
 *   <li><b>Message Boards</b> — the eight colour-coded group boards; double-click
 *       opens (or focuses) that group's board window.</li>
 * </ol>
 */
public final class AgentListPanel extends JPanel implements SwarmModel.SwarmModelListener {

    private final SwarmModel model;
    private final ThemeManager theme;
    private final AgentActions actions;

    private final DefaultListModel<Agent> listModel = new DefaultListModel<>();
    private final JList<Agent> list = new JList<>(listModel);

    private final DefaultListModel<Agent> autoListModel = new DefaultListModel<>();
    private final JList<Agent> autoList = new JList<>(autoListModel);

    private final DefaultListModel<AgentGroup> boardListModel = new DefaultListModel<>();
    private final JList<AgentGroup> boardList = new JList<>(boardListModel);

    public AgentListPanel(SwarmModel model, ThemeManager theme, AgentActions actions) {
        super(new GridBagLayout());
        this.model = model;
        this.theme = theme;
        this.actions = actions;

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;

        c.gridy = 0;
        c.weighty = 1.0;
        add(buildNamedAgentsSection(), c);

        c.gridy = 1;
        c.weighty = 0.0;
        add(buildAutoAgentsSection(), c);

        c.gridy = 2;
        c.weighty = 0.0;
        add(buildMessageBoardsSection(), c);

        installAgentMouse();
        installBoardMouse();

        model.addListener(this);
        refresh();
    }

    // ------------------------------------------------------------------
    // Section builders
    // ------------------------------------------------------------------

    private static JLabel sectionHeader(String text) {
        JLabel header = new JLabel(text);
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        return header;
    }

    private JComponent buildNamedAgentsSection() {
        JPanel section = new JPanel(new BorderLayout());

        // Header (with a purge-all trash button) + launch button stacked at the
        // top of the section.
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.add(sectionHeader("Named Agents"), BorderLayout.CENTER);
        JPanel headerButtons = new JPanel(new BorderLayout());
        headerButtons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 8));
        headerButtons.add(buildPurgeAllButton(), BorderLayout.CENTER);
        headerRow.add(headerButtons, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout());
        top.add(headerRow, BorderLayout.NORTH);
        top.add(buildLaunchBar(), BorderLayout.SOUTH);
        section.add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AgentCell());
        list.setFixedCellHeight(46);
        section.add(new JScrollPane(list), BorderLayout.CENTER);
        return section;
    }

    private JComponent buildAutoAgentsSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.add(sectionHeader("Auto Agents"), BorderLayout.NORTH);

        autoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        autoList.setCellRenderer(new AgentCell());
        autoList.setFixedCellHeight(46);
        JScrollPane scroll = new JScrollPane(autoList);
        // Reserve a compact-but-visible area while empty; grows with auto agents.
        scroll.setPreferredSize(new Dimension(0, 92));
        scroll.setMinimumSize(new Dimension(0, 48));
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JComponent buildMessageBoardsSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.add(sectionHeader("Message Boards"), BorderLayout.NORTH);

        for (AgentGroup g : AgentGroup.all()) {
            boardListModel.addElement(g);
        }
        boardList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        boardList.setCellRenderer(new BoardCell());
        boardList.setFixedCellHeight(26);
        boardList.setToolTipText("Double-click a board to open it as a window");
        JScrollPane scroll = new JScrollPane(boardList);
        // All eight board rows are always shown in full (never shrunk away).
        int boardsHeight = AgentGroup.all().size() * 26 + 4;
        Dimension boardsSize = new Dimension(0, boardsHeight);
        scroll.setPreferredSize(boardsSize);
        scroll.setMinimumSize(boardsSize);
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JComponent buildPurgeAllButton() {
        JButton trash = new JButton("\uD83D\uDDD1"); // 🗑 wastebasket
        trash.setToolTipText("Purge all stale agents");
        trash.setMargin(new java.awt.Insets(0, 6, 0, 6));
        trash.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 8));
        trash.setFocusPainted(false);
        trash.addActionListener(e -> confirmPurgeAll());
        return trash;
    }

    private void confirmPurgeAll() {
        List<Agent> stale = new java.util.ArrayList<>();
        for (Agent a : model.agentsSorted()) {
            if (!a.getStatus().isLive()) {
                stale.add(a);
            }
        }
        if (stale.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "There are no offline agents to purge.",
                    "Purge all stale agents", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Purge all stale agents?\n\n"
                        + "This clears the retained MQTT registry topic for "
                        + stale.size() + " offline agent" + (stale.size() == 1 ? "" : "s")
                        + ",\nremoving them for every connected client. Live agents are\n"
                        + "left untouched (a running agent re-registers itself).",
                "Purge all stale agents", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice == javax.swing.JOptionPane.OK_OPTION) {
            for (Agent a : stale) {
                actions.purge(a);
            }
        }
    }

    private JComponent buildLaunchBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        JButton launch = new JButton("＋ Launch agent ▾");
        launch.setToolTipText("Spawn a new agent on a running console host");
        launch.addActionListener(e -> showLaunchMenu(launch));
        bar.add(launch, BorderLayout.CENTER);
        return bar;
    }

    private void showLaunchMenu(JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem defaults = new JMenuItem("<defaults>");
        defaults.addActionListener(ev -> actions.launchAgent(null));
        menu.add(defaults);

        for (Profile p : actions.launchProfiles()) {
            JMenuItem item = new JMenuItem(p.getName() == null || p.getName().isBlank()
                    ? "(unnamed profile)" : p.getName());
            item.addActionListener(ev -> actions.launchAgent(p));
            menu.add(item);
        }

        menu.addSeparator();
        JMenuItem manage = new JMenuItem("Manage profiles…");
        manage.addActionListener(ev -> actions.openProfileManager());
        menu.add(manage);

        // Anchored beneath the launch button (it now sits at the top of the section).
        menu.show(anchor, 0, anchor.getHeight());
    }

    // ------------------------------------------------------------------
    // Mouse handling
    // ------------------------------------------------------------------

    private void installAgentMouse() {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Agent a = list.getSelectedValue();
                    if (a != null) {
                        actions.openMonitor(a);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }
        });
    }

    private void installBoardMouse() {
        boardList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    AgentGroup g = boardList.getSelectedValue();
                    if (g != null) {
                        actions.openBoard(g);
                    }
                }
            }
        });
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int index = list.locationToIndex(e.getPoint());
        if (index < 0) {
            return;
        }
        list.setSelectedIndex(index);
        Agent agent = listModel.get(index);
        showMenu(agent, e);
    }

    private void showMenu(Agent agent, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem monitor = new JMenuItem("Open monitor");
        monitor.addActionListener(ev -> actions.openMonitor(agent));
        menu.add(monitor);

        JMenuItem controls = new JMenuItem("Controls / details…");
        controls.addActionListener(ev -> actions.openControls(agent));
        menu.add(controls);

        JMenuItem rename = new JMenuItem("Rename…");
        rename.addActionListener(ev -> promptRename(agent));
        menu.add(rename);

        JMenuItem stop = new JMenuItem("Stop current turn");
        stop.setEnabled(agent.getStatus().isBusy());
        stop.addActionListener(ev -> actions.stop(agent));
        menu.add(stop);

        menu.addSeparator();

        List<ModelRef> models = actions.selectableModels(agent);
        JMenuItem setModelLabel = new JMenuItem("Set model…");
        setModelLabel.setEnabled(false);
        menu.add(setModelLabel);
        if (models.isEmpty()) {
            JMenuItem none = new JMenuItem("   (no models reported)");
            none.setEnabled(false);
            menu.add(none);
        } else {
            for (ModelRef m : models) {
                JMenuItem item = new JMenuItem("   " + m.displayLabel());
                if (m.equals(agent.getModel())) {
                    item.setFont(item.getFont().deriveFont(Font.BOLD));
                    item.setText("   ● " + m.displayLabel());
                }
                item.addActionListener(ev -> actions.setModel(agent, m));
                menu.add(item);
            }
        }

        menu.addSeparator();
        JMenuItem reset = new JMenuItem("Reset context");
        reset.addActionListener(ev -> actions.reset(agent));
        menu.add(reset);

        JMenuItem quit = new JMenuItem("Shut down agent (/quit)");
        quit.addActionListener(ev -> confirmQuit(agent));
        menu.add(quit);

        // Purge is only meaningful for stale/offline agents: it clears the
        // retained registry topic so the dead agent disappears everywhere.
        boolean offline = !agent.getStatus().isLive();
        JMenuItem purge = new JMenuItem("Purge stale agent");
        purge.setToolTipText(offline
                ? "Clear this offline agent's retained MQTT registry topic"
                : "Only available for offline agents");
        purge.setEnabled(offline);
        purge.addActionListener(ev -> confirmPurge(agent));
        menu.add(purge);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void confirmQuit(Agent agent) {
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Shut down agent \"" + agent.getName() + "\"?\nThis is equivalent to /quit and stops the agent process.",
                "Shut down agent", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice == javax.swing.JOptionPane.OK_OPTION) {
            actions.quit(agent);
        }
    }

    private void confirmPurge(Agent agent) {
        if (agent.getStatus().isLive()) {
            return;
        }
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Purge offline agent \"" + agent.getName() + "\"?\n"
                        + "This clears its retained MQTT registry topic so the stale\n"
                        + "agent is removed for every connected client. It does not\n"
                        + "affect a live agent (a running agent re-registers itself).",
                "Purge stale agent", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice == javax.swing.JOptionPane.OK_OPTION) {
            actions.purge(agent);
        }
    }

    private void promptRename(Agent agent) {
        String current = agent.getName();
        Object input = javax.swing.JOptionPane.showInputDialog(this, "New name for this agent:",
                "Rename agent", javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (input != null) {
            String name = input.toString().trim();
            if (!name.isEmpty() && !name.equals(current)) {
                actions.rename(agent, name);
            }
        }
    }

    private void refresh() {
        Agent selected = list.getSelectedValue();
        listModel.clear();
        for (Agent a : model.agentsSorted()) {
            listModel.addElement(a);
        }
        if (selected != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getId().equals(selected.getId())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    @Override
    public void agentsChanged() {
        refresh();
    }

    @Override
    public void agentUpdated(Agent agent) {
        list.repaint();
    }

    // ------------------------------------------------------------------

    /** Renders an agent row: status dot, name, model/status subtitle, and group icon. */
    private final class AgentCell extends JPanel implements ListCellRenderer<Agent> {
        private final StatusDot dot = new StatusDot();
        private final JLabel name = new JLabel();
        private final JLabel subtitle = new JLabel();
        private final ChainIcon groupIcon = new ChainIcon(16, AgentGroup.DEFAULT.color());
        private final JLabel groupLabel = new JLabel();

        AgentCell() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
            text.add(name);
            text.add(subtitle);
            groupLabel.setIcon(groupIcon);
            add(dot, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(groupLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Agent> list, Agent value,
                int index, boolean isSelected, boolean cellHasFocus) {
            name.setText(value.getName());
            subtitle.setText(value.getStatus().label() + "  ·  " + value.modelLabel());
            dot.setColor(StatusDot.colorFor(value.getStatus(), theme));
            AgentGroup group = model.groupOf(value.getId());
            groupIcon.setColor(group.color());
            groupLabel.setToolTipText("Group: " + group.label());

            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            setBackground(bg);
            setOpaque(true);
            name.setForeground(fg);
            subtitle.setForeground(isSelected ? fg : theme.muted());
            return this;
        }
    }

    /** Renders a message-board row: a colour-coded chain icon plus the group name. */
    private final class BoardCell extends DefaultListCellRenderer {
        private final ChainIcon icon = new ChainIcon(16, AgentGroup.DEFAULT.color());

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            AgentGroup g = (AgentGroup) value;
            icon.setColor(g.color());
            setIcon(icon);
            setText(g.label() + " board");
            setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            setIconTextGap(8);
            return this;
        }
    }

}
