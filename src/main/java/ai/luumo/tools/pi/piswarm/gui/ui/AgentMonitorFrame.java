package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentGroup;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * A tileable internal window monitoring a single agent: a pi-style output feed
 * plus controls to stop the agent, toggle its model, and send messages.
 */
public final class AgentMonitorFrame extends JInternalFrame implements SwarmModel.SwarmModelListener {

    private final SwarmModel model;
    private final ThemeManager theme;
    private final AgentActions actions;
    private final String agentId;

    private final PiOutputPane output = new PiOutputPane();
    private final PiEventRenderer renderer;
    private final JLabel statusLabel = new JLabel();
    private final JLabel cwdLabel = new JLabel();
    private final GroupChooserButton groupButton;
    private final StatusDot statusDot = new StatusDot();
    private final JButton stopButton = new JButton("Stop");
    private final HoldButton killButton;
    private final JButton modelButton = new JButton("Model ▾");
    private final JButton renameButton = new JButton("Rename…");
    private final JButton controlsButton = new JButton("Controls…");
    private final MessageInput input = new MessageInput();
    private final JCheckBox urgent = new JCheckBox("urgent");

    public AgentMonitorFrame(Agent agent, SwarmModel model, ThemeManager theme, AgentActions actions) {
        super(title(agent), true, true, true, true);
        this.model = model;
        this.theme = theme;
        this.actions = actions;
        this.agentId = agent.getId();
        this.renderer = new PiEventRenderer(theme, output);
        this.killButton = new HoldButton("Kill", 1000, this::killAgent);
        this.killButton.setToolTipText("Hold for 1s to /quit this agent and close the window");
        this.groupButton = new GroupChooserButton(model.groupOf(agentId),
                g -> withAgent(a -> actions.setGroup(a, g)));

        setFrameIcon(null);
        setSize(560, 420);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.add(buildToolbar(), BorderLayout.NORTH);
        top.add(buildCwdBar(), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(output, BorderLayout.CENTER);
        add(buildInput(), BorderLayout.SOUTH);

        replayHistory();
        updateStatus(agent);

        model.addListener(this);
        addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                // Focusing the window (click / select) drops the cursor straight
                // into the message box.
                input.focusInput();
            }

            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                model.removeListener(AgentMonitorFrame.this);
            }
        });
    }

    private static String title(Agent agent) {
        return "Agent · " + agent.getName();
    }

    public String getAgentId() {
        return agentId;
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        stopButton.addActionListener(e -> withAgent(actions::stop));
        modelButton.addActionListener(e -> withAgent(this::showModelMenu));
        renameButton.addActionListener(e -> withAgent(this::promptRename));
        controlsButton.addActionListener(e -> withAgent(actions::openControls));

        bar.add(groupButton);
        bar.add(javax.swing.Box.createHorizontalStrut(6));
        statusDot.setPreferredSize(new java.awt.Dimension(14, 14));
        statusDot.setMaximumSize(new java.awt.Dimension(14, 16));
        bar.add(statusDot);
        bar.add(javax.swing.Box.createHorizontalStrut(4));
        bar.add(statusLabel);
        bar.add(javax.swing.Box.createHorizontalGlue());
        bar.add(stopButton);
        bar.add(killButton);
        bar.add(modelButton);
        bar.add(renameButton);
        bar.add(controlsButton);
        return bar;
    }

    /** A slim strip under the toolbar showing the agent's working directory. */
    private JPanel buildCwdBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        cwdLabel.setFont(cwdLabel.getFont().deriveFont(Font.PLAIN,
                cwdLabel.getFont().getSize2D() - 1f));
        cwdLabel.setForeground(java.awt.Color.GRAY);
        panel.add(cwdLabel, BorderLayout.WEST);
        return panel;
    }

    private JPanel buildInput() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JButton send = new JButton("Send");
        send.addActionListener(e -> submit());
        input.setOnSubmit(this::submit);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(urgent);
        right.add(send);

        panel.add(new JLabel("Message: "), BorderLayout.WEST);
        panel.add(input, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        Agent agent = model.agent(agentId);
        if (agent == null) {
            return;
        }
        if (urgent.isSelected()) {
            actions.interrupt(agent, text);
            renderer.renderOutgoing("[interrupt]", text);
        } else {
            actions.sendMessage(agent, text);
            renderer.renderOutgoing("[in]", text);
        }
        input.setText("");
        urgent.setSelected(false);
    }

    /** Always present the selectable models as a popup list under the button. */
    private void showModelMenu(Agent agent) {
        java.util.List<ai.luumo.tools.pi.piswarm.gui.config.ModelRef> models =
                actions.selectableModels(agent);
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        if (models.isEmpty()) {
            javax.swing.JMenuItem none = new javax.swing.JMenuItem("(no models reported)");
            none.setEnabled(false);
            menu.add(none);
        } else {
            for (ai.luumo.tools.pi.piswarm.gui.config.ModelRef m : models) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(m.displayLabelWithProvider());
                if (m.equals(agent.getModel())) {
                    item.setFont(item.getFont().deriveFont(Font.BOLD));
                    item.setText("● " + m.displayLabelWithProvider());
                }
                item.addActionListener(ev -> withAgent(a -> actions.setModel(a, m)));
                menu.add(item);
            }
        }
        menu.show(modelButton, 0, modelButton.getHeight());
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

    /** Shut the agent down (/quit) and close this monitor window. */
    private void killAgent() {
        withAgent(actions::quit);
        dispose();
    }

    private void withAgent(java.util.function.Consumer<Agent> action) {
        Agent agent = model.agent(agentId);
        if (agent != null) {
            action.accept(agent);
        }
    }

    private void replayHistory() {
        renderer.renderInfo("── replaying recent activity ──");
        for (AgentEvent event : model.eventsFor(agentId)) {
            renderer.render(event);
        }
    }

    private void updateStatus(Agent agent) {
        if (agent == null) {
            statusLabel.setText("offline");
            statusDot.setColor(StatusDot.colorFor(ai.luumo.tools.pi.piswarm.gui.model.AgentStatus.OFFLINE, theme));
            statusDot.repaint();
            cwdLabel.setText(" ");
            stopButton.setEnabled(false);
            return;
        }
        statusDot.setColor(StatusDot.colorFor(agent.getStatus(), theme));
        statusDot.repaint();
        String cwd = agent.getCwd();
        if (cwd == null || cwd.isBlank()) {
            cwdLabel.setText("(unknown)");
        } else {
            cwdLabel.setText(cwd);
        }
        cwdLabel.setToolTipText(cwd);
        statusLabel.setText(" " + agent.getStatus().label() + "  ·  " + agent.modelLabel() + "  ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
        stopButton.setEnabled(agent.getStatus().isBusy());
        modelButton.setEnabled(true);
        groupButton.setGroup(model.groupOf(agentId));
    }

    // ------------------------------------------------------------------
    // SwarmModel listener
    // ------------------------------------------------------------------

    @Override
    public void agentEvent(AgentEvent event) {
        if (event.agentId().equals(agentId)) {
            renderer.render(event);
        }
    }

    @Override
    public void agentUpdated(Agent agent) {
        if (agent.getId().equals(agentId)) {
            updateStatus(agent);
            setTitle(title(agent));
        }
    }
}
