package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.config.Profile;
import ai.luumo.tools.pi.piswarm.gui.config.ProfilesConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor for the named launch {@link Profile profiles} persisted to
 * {@code profiles-config.json}. A profile bundles an optional agent name, model
 * and extra extensions used when spawning a new agent.
 */
public final class ProfileManagerDialog extends JDialog {

    private final ProfilesConfig config;
    private final Runnable onSave;

    private final DefaultListModel<Profile> listModel = new DefaultListModel<>();
    private final JList<Profile> list = new JList<>(listModel);

    private final JTextField nameField = new JTextField();
    private final JTextField agentNameField = new JTextField();
    private final JTextField modelField = new JTextField();
    private final JTextArea extensionsArea = new JTextArea(5, 20);

    private Profile current;
    private boolean loading;

    public ProfileManagerDialog(Frame owner, ProfilesConfig config, Runnable onSave) {
        super(owner, "Launch profiles", false);
        this.config = config;
        this.onSave = onSave;

        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildList(), BorderLayout.WEST);
        add(buildEditor(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        for (Profile p : config.getProfiles()) {
            listModel.addElement(p);
        }
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                applyEdits();
                loadSelected();
            }
        });

        setSize(640, 420);
        setLocationRelativeTo(owner);
        loadSelected();
    }

    private JPanel buildList() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setPreferredSize(new Dimension(200, 0));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton add = new JButton("New");
        add.addActionListener(e -> addProfile());
        JButton remove = new JButton("Delete");
        remove.addActionListener(e -> deleteSelected());
        buttons.add(add);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildEditor() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;

        addRow(panel, c, "Profile name:", nameField);
        addRow(panel, c, "Agent name (optional):", agentNameField);
        addRow(panel, c, "Model (optional):", modelField);

        // Extensions header + multiline area.
        c.gridx = 0;
        c.gridwidth = 2;
        JLabel extLabel = new JLabel("Extensions (one per line, optional):");
        panel.add(extLabel, c);
        c.gridy++;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        extensionsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(extensionsArea), c);

        modelField.setToolTipText("e.g. anthropic/claude-sonnet-4-5  or a fuzzy query like  sonnet");
        agentNameField.setToolTipText("Used as the spawned agent's --name; leave blank for a default name");
        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints c, String label, JTextField field) {
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        panel.add(new JLabel(label), c);
        c.gridy++;
        panel.add(field, c);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            applyEdits();
            persist();
        });
        JButton close = new JButton("Save & close");
        close.addActionListener(e -> {
            applyEdits();
            persist();
            dispose();
        });
        footer.add(save);
        footer.add(close);
        return footer;
    }

    // ------------------------------------------------------------------

    private void addProfile() {
        applyEdits();
        Profile p = new Profile("New profile");
        config.getProfiles().add(p);
        listModel.addElement(p);
        list.setSelectedValue(p, true);
    }

    private void deleteSelected() {
        Profile sel = list.getSelectedValue();
        if (sel == null) {
            return;
        }
        config.getProfiles().remove(sel);
        listModel.removeElement(sel);
        current = null;
        loadSelected();
        persist();
    }

    private void loadSelected() {
        loading = true;
        try {
            current = list.getSelectedValue();
            boolean has = current != null;
            nameField.setEnabled(has);
            agentNameField.setEnabled(has);
            modelField.setEnabled(has);
            extensionsArea.setEnabled(has);
            if (!has) {
                nameField.setText("");
                agentNameField.setText("");
                modelField.setText("");
                extensionsArea.setText("");
                return;
            }
            nameField.setText(current.getName());
            agentNameField.setText(current.getAgentName());
            modelField.setText(current.getModel());
            extensionsArea.setText(String.join("\n", current.getExtensions()));
        } finally {
            loading = false;
        }
    }

    private void applyEdits() {
        if (loading || current == null) {
            return;
        }
        current.setName(nameField.getText().trim());
        current.setAgentName(agentNameField.getText().trim());
        current.setModel(modelField.getText().trim());
        List<String> exts = new ArrayList<>();
        for (String line : extensionsArea.getText().split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                exts.add(t);
            }
        }
        current.setExtensions(exts);
        list.repaint();
    }

    private void persist() {
        if (onSave != null) {
            onSave.run();
        }
    }
}
