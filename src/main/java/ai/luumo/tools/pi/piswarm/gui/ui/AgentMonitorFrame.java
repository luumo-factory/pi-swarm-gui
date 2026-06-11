package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
    private final JButton stopButton = new JButton("Stop");
    private final JButton modelButton = new JButton("Toggle model");
    private final JButton controlsButton = new JButton("Controls…");
    private final JTextField input = new JTextField();
    private final JCheckBox urgent = new JCheckBox("urgent");

    public AgentMonitorFrame(Agent agent, SwarmModel model, ThemeManager theme, AgentActions actions) {
        super(title(agent), true, true, true, true);
        this.model = model;
        this.theme = theme;
        this.actions = actions;
        this.agentId = agent.getId();
        this.renderer = new PiEventRenderer(theme, output);

        setFrameIcon(null);
        setSize(560, 420);
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);
        add(output, BorderLayout.CENTER);
        add(buildInput(), BorderLayout.SOUTH);

        replayHistory();
        updateStatus(agent);

        model.addListener(this);
        addInternalFrameListener(new InternalFrameAdapter() {
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
        modelButton.addActionListener(e -> withAgent(actions::toggleModel));
        controlsButton.addActionListener(e -> withAgent(actions::openControls));

        bar.add(statusLabel);
        bar.add(javax.swing.Box.createHorizontalGlue());
        bar.add(stopButton);
        bar.add(modelButton);
        bar.add(controlsButton);
        return bar;
    }

    private JPanel buildInput() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JButton send = new JButton("Send");
        send.addActionListener(e -> submit());
        input.addActionListener(e -> submit());

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
            stopButton.setEnabled(false);
            return;
        }
        statusLabel.setText(" " + agent.getStatus().label() + "  ·  " + agent.modelLabel() + "  ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
        stopButton.setEnabled(agent.getStatus().isBusy());
        modelButton.setEnabled(agent.getAvailableModels().size() > 1);
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
