package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.ModelRef;
import ai.luumo.tools.pi.piswarm.gui.model.Agent;
import ai.luumo.tools.pi.piswarm.gui.model.ExtensionInfo;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;

/**
 * Read/write control panel for a single agent, exercising the control plane:
 * switch model, enable/disable extensions and tools, and request a fresh status.
 * Rebuilds itself whenever the agent's registry record changes.
 */
public final class AgentControlDialog extends JDialog implements SwarmModel.SwarmModelListener {

    private final SwarmModel model;
    private final AgentActions actions;
    private final String agentId;

    private final JLabel statusLabel = new JLabel();
    private final JComboBox<ModelRef> modelCombo = new JComboBox<>();
    private final JPanel extensionsPanel = new JPanel();
    private final JPanel toolsPanel = new JPanel();
    private boolean updating;

    public AgentControlDialog(Frame owner, Agent agent, SwarmModel model, AgentActions actions) {
        super(owner, "Controls · " + agent.getName(), false);
        this.model = model;
        this.actions = actions;
        this.agentId = agent.getId();

        setLayout(new BorderLayout(0, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        setSize(420, 560);
        setLocationRelativeTo(owner);

        model.addListener(this);
        rebuild(agent);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        header.add(statusLabel, BorderLayout.NORTH);

        JPanel modelRow = new JPanel(new BorderLayout(6, 0));
        modelRow.add(new JLabel("Model:"), BorderLayout.WEST);
        modelCombo.addActionListener(e -> {
            if (updating) {
                return;
            }
            ModelRef sel = (ModelRef) modelCombo.getSelectedItem();
            Agent agent = model.agent(agentId);
            if (sel != null && agent != null && !sel.equals(agent.getModel())) {
                actions.setModel(agent, sel);
            }
        });
        modelRow.add(modelCombo, BorderLayout.CENTER);
        header.add(modelRow, BorderLayout.SOUTH);
        return header;
    }

    private JScrollPane buildCenter() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(sectionLabel("Extensions"));
        extensionsPanel.setLayout(new BoxLayout(extensionsPanel, BoxLayout.Y_AXIS));
        extensionsPanel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(extensionsPanel);

        content.add(Box.createVerticalStrut(12));
        content.add(sectionLabel("Tools"));
        toolsPanel.setLayout(new BoxLayout(toolsPanel, BoxLayout.Y_AXIS));
        toolsPanel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(toolsPanel);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton refresh = new JButton("Request status");
        refresh.addActionListener(e -> {
            Agent agent = model.agent(agentId);
            if (agent != null) {
                actions.requestStatus(agent);
            }
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        footer.add(refresh);
        footer.add(close);
        return footer;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return label;
    }

    private void rebuild(Agent agent) {
        updating = true;
        try {
            if (agent == null) {
                statusLabel.setText("agent offline");
                modelCombo.setModel(new DefaultComboBoxModel<>());
                extensionsPanel.removeAll();
                toolsPanel.removeAll();
                revalidate();
                repaint();
                return;
            }

            statusLabel.setText(agent.getName() + "  ·  " + agent.getStatus().label());

            DefaultComboBoxModel<ModelRef> models = new DefaultComboBoxModel<>();
            for (ModelRef m : agent.getAvailableModels()) {
                models.addElement(m);
            }
            modelCombo.setModel(models);
            modelCombo.setEnabled(models.getSize() > 0);
            if (agent.getModel() != null) {
                modelCombo.setSelectedItem(agent.getModel());
            }

            extensionsPanel.removeAll();
            if (agent.getExtensions().isEmpty()) {
                extensionsPanel.add(mutedLine("(none reported)"));
            }
            for (ExtensionInfo ext : agent.getExtensions()) {
                extensionsPanel.add(extensionRow(agent, ext));
            }

            toolsPanel.removeAll();
            List<String> available = agent.getTools().available();
            if (available.isEmpty()) {
                toolsPanel.add(mutedLine("(none reported)"));
            }
            for (String tool : available) {
                toolsPanel.add(toolRow(agent, tool));
            }

            revalidate();
            repaint();
        } finally {
            updating = false;
        }
    }

    private JComponent extensionRow(Agent agent, ExtensionInfo ext) {
        String label = ext.shortName()
                + (ext.tools().isEmpty() ? "" : "  (" + ext.tools().size() + " tools)");
        JCheckBox box = new JCheckBox(label, ext.active());
        box.setToolTipText(ext.id());
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.setEnabled(!ext.tools().isEmpty());
        box.addActionListener(e -> {
            if (!updating) {
                actions.setExtensionEnabled(agent, ext.id(), box.isSelected());
            }
        });
        return box;
    }

    private JComponent toolRow(Agent agent, String tool) {
        JCheckBox box = new JCheckBox(tool, agent.getTools().isActive(tool));
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.addActionListener(e -> {
            if (!updating) {
                actions.setToolsEnabled(agent, List.of(tool), box.isSelected());
            }
        });
        return box;
    }

    private JLabel mutedLine(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(420, 560);
    }

    // ------------------------------------------------------------------

    @Override
    public void agentUpdated(Agent agent) {
        if (agent.getId().equals(agentId)) {
            rebuild(agent);
        }
    }

    @Override
    public void dispose() {
        model.removeListener(this);
        super.dispose();
    }
}
