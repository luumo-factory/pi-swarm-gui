package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
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
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Left-hand panel listing all known agents with their model and busy/idle state.
 * Double-click opens a monitor; right-click exposes stop / toggle model / reset.
 */
public final class AgentListPanel extends JPanel implements SwarmModel.SwarmModelListener {

    private final SwarmModel model;
    private final ThemeManager theme;
    private final AgentActions actions;

    private final DefaultListModel<Agent> listModel = new DefaultListModel<>();
    private final JList<Agent> list = new JList<>(listModel);

    public AgentListPanel(SwarmModel model, ThemeManager theme, AgentActions actions) {
        super(new BorderLayout());
        this.model = model;
        this.theme = theme;
        this.actions = actions;

        JLabel header = new JLabel("Agents");
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        add(header, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AgentCell());
        list.setFixedCellHeight(46);
        add(new JScrollPane(list), BorderLayout.CENTER);

        installMouse();

        model.addListener(this);
        refresh();
    }

    private void installMouse() {
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

        JMenuItem stop = new JMenuItem("Stop current turn");
        stop.setEnabled(agent.getStatus().isBusy());
        stop.addActionListener(ev -> actions.stop(agent));
        menu.add(stop);

        menu.addSeparator();

        List<ModelRef> models = agent.getAvailableModels();
        JMenuItem toggle = new JMenuItem("Toggle model (next)");
        toggle.setEnabled(models.size() > 1);
        toggle.addActionListener(ev -> actions.toggleModel(agent));
        menu.add(toggle);

        if (!models.isEmpty()) {
            JPopupMenu sub = new JPopupMenu();
            JMenuItem setModelLabel = new JMenuItem("Set model…");
            setModelLabel.setEnabled(false);
            menu.add(setModelLabel);
            for (ModelRef m : models) {
                JMenuItem item = new JMenuItem("   " + m.displayLabel());
                if (m.equals(agent.getModel())) {
                    item.setFont(item.getFont().deriveFont(Font.BOLD));
                }
                item.addActionListener(ev -> actions.setModel(agent, m));
                menu.add(item);
            }
        }

        menu.addSeparator();
        JMenuItem reset = new JMenuItem("Reset context");
        reset.addActionListener(ev -> actions.reset(agent));
        menu.add(reset);

        menu.show(e.getComponent(), e.getX(), e.getY());
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

    private Color statusColor(AgentStatus status) {
        return switch (status) {
            case BUSY -> theme.warning();
            case IDLE, ONLINE -> theme.success();
            case OFFLINE -> theme.error();
            case UNKNOWN -> theme.muted();
        };
    }

    /** Renders an agent row: status dot, name, and model/status subtitle. */
    private final class AgentCell extends JPanel implements ListCellRenderer<Agent> {
        private final StatusDot dot = new StatusDot();
        private final JLabel name = new JLabel();
        private final JLabel subtitle = new JLabel();

        AgentCell() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
            text.add(name);
            text.add(subtitle);
            add(dot, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Agent> list, Agent value,
                int index, boolean isSelected, boolean cellHasFocus) {
            name.setText(value.getName());
            subtitle.setText(value.getStatus().label() + "  ·  " + value.modelLabel());
            dot.color = statusColor(value.getStatus());

            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            setBackground(bg);
            setOpaque(true);
            name.setForeground(fg);
            subtitle.setForeground(isSelected ? fg : theme.muted());
            return this;
        }
    }

    private static final class StatusDot extends JComponent {
        Color color = Color.GRAY;

        StatusDot() {
            setPreferredSize(new Dimension(14, 14));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int d = 10;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            g2.setColor(color);
            g2.fillOval(x, y, d, d);
            g2.dispose();
        }
    }
}
