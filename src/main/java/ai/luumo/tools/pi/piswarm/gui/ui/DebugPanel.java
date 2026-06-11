package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.RawMessage;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug view of the raw MQTT traffic. A topic tree on the left; the messages for
 * the selected topic (and everything beneath it) in the middle; the decoded /
 * pretty-printed body of the selected message on the right.
 *
 * <p>Registers as a {@link SwarmModel.SwarmModelListener} so it updates live
 * while open, and detaches via {@link #dispose()} when the window is closed.</p>
 */
public final class DebugPanel extends JPanel implements SwarmModel.SwarmModelListener {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final SwarmModel model;
    private final ObjectMapper mapper = new ObjectMapper();

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(new TopicNode("topics", ""));
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();

    private final DefaultListModel<RawMessage> listModel = new DefaultListModel<>();
    private final JList<RawMessage> messageList = new JList<>(listModel);
    private final JTextArea detail = new JTextArea();
    private final JLabel countLabel = new JLabel();
    private final JCheckBox follow = new JCheckBox("Follow", true);

    private String selectedPath = "";

    public DebugPanel(SwarmModel model) {
        super(new BorderLayout());
        this.model = model;

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> onTopicSelected());

        messageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messageList.setCellRenderer(new MessageRenderer());
        messageList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail(messageList.getSelectedValue());
            }
        });

        detail.setEditable(false);
        detail.setLineWrap(false);
        detail.setFont(monospaced());

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setMinimumSize(new Dimension(160, 0));

        JPanel middle = new JPanel(new BorderLayout());
        middle.add(new JScrollPane(messageList), BorderLayout.CENTER);
        middle.add(countLabel, BorderLayout.SOUTH);
        countLabel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        countLabel.setFont(countLabel.getFont().deriveFont(11f));

        JSplitPane listDetail = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, middle, new JScrollPane(detail));
        listDetail.setResizeWeight(0.4);
        listDetail.setDividerLocation(320);

        JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, listDetail);
        outer.setDividerLocation(240);

        add(buildToolbar(), BorderLayout.NORTH);
        add(outer, BorderLayout.CENTER);

        // Seed from whatever has already been captured before the window opened.
        for (String topic : model.rawTopics()) {
            ensureTopic(topic);
        }
        treeModel.reload();
        expandAll();
        refreshList();

        model.addListener(this);
    }

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton clear = new JButton("Clear view");
        clear.setToolTipText("Clear the currently shown message list (does not drop captured history)");
        clear.addActionListener(e -> {
            listModel.clear();
            detail.setText("");
            updateCount();
        });
        JButton expand = new JButton("Expand all");
        expand.addActionListener(e -> expandAll());
        bar.add(follow);
        bar.addSeparator();
        bar.add(expand);
        bar.add(clear);
        return bar;
    }

    /** Detach from the model; call when the hosting window closes. */
    public void dispose() {
        model.removeListener(this);
    }

    // ------------------------------------------------------------------
    // Tree maintenance
    // ------------------------------------------------------------------

    /** Ensure a node exists for {@code topic} and every ancestor; returns its node. */
    private DefaultMutableTreeNode ensureTopic(String topic) {
        DefaultMutableTreeNode existing = nodes.get(topic);
        if (existing != null) {
            return existing;
        }
        String[] segments = topic.split("/");
        DefaultMutableTreeNode parent = root;
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(segment);
            String full = path.toString();
            DefaultMutableTreeNode node = nodes.get(full);
            if (node == null) {
                node = new DefaultMutableTreeNode(new TopicNode(segment, full));
                insertSorted(parent, node);
                nodes.put(full, node);
            }
            parent = node;
        }
        return parent;
    }

    private void insertSorted(DefaultMutableTreeNode parent, DefaultMutableTreeNode child) {
        String childName = ((TopicNode) child.getUserObject()).segment;
        int idx = 0;
        while (idx < parent.getChildCount()) {
            DefaultMutableTreeNode sibling = (DefaultMutableTreeNode) parent.getChildAt(idx);
            String name = ((TopicNode) sibling.getUserObject()).segment;
            if (name.compareToIgnoreCase(childName) > 0) {
                break;
            }
            idx++;
        }
        treeModel.insertNodeInto(child, parent, idx);
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void onTopicSelected() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            selectedPath = "";
        } else {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            selectedPath = ((TopicNode) node.getUserObject()).fullPath;
        }
        refreshList();
    }

    /** True when {@code topic} is at or below the currently selected node. */
    private boolean covered(String topic) {
        if (selectedPath.isEmpty()) {
            return true; // root selected (or nothing) => all topics
        }
        return topic.equals(selectedPath) || topic.startsWith(selectedPath + "/");
    }

    // ------------------------------------------------------------------
    // Message list
    // ------------------------------------------------------------------

    private void refreshList() {
        listModel.clear();
        List<RawMessage> all = new java.util.ArrayList<>();
        for (String topic : model.rawTopics()) {
            if (covered(topic)) {
                all.addAll(model.rawMessagesFor(topic));
            }
        }
        all.sort(java.util.Comparator.comparingLong(RawMessage::ts));
        for (RawMessage m : all) {
            listModel.addElement(m);
        }
        updateCount();
        if (follow.isSelected() && !listModel.isEmpty()) {
            int last = listModel.size() - 1;
            messageList.setSelectedIndex(last);
            messageList.ensureIndexIsVisible(last);
        }
    }

    private void updateCount() {
        String scope = selectedPath.isEmpty() ? "all topics" : selectedPath;
        countLabel.setText(listModel.size() + " message(s)  ·  " + scope);
    }

    private void showDetail(RawMessage message) {
        if (message == null) {
            detail.setText("");
            return;
        }
        StringBuilder header = new StringBuilder();
        header.append("topic:    ").append(message.topic()).append('\n');
        header.append("time:     ").append(TIME.format(Instant.ofEpochMilli(message.ts()))).append('\n');
        header.append("qos:      ").append(message.qos())
                .append(message.retained() ? "   (retained)" : "").append('\n');
        header.append("bytes:    ").append(message.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .append('\n');
        header.append("------------------------------------------------------------\n");
        detail.setText(header + formatBody(message.payload()));
        detail.setCaretPosition(0);
    }

    private String formatBody(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "(empty payload)";
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode node = mapper.readTree(payload);
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception ignored) {
                // not valid JSON; fall through to raw text
            }
        }
        return payload;
    }

    // ------------------------------------------------------------------
    // SwarmModel listener (EDT)
    // ------------------------------------------------------------------

    @Override
    public void rawMessage(RawMessage message) {
        boolean newTopic = !nodes.containsKey(message.topic());
        DefaultMutableTreeNode node = ensureTopic(message.topic());
        if (newTopic) {
            tree.expandPath(new TreePath(((DefaultMutableTreeNode) node.getParent()).getPath()));
        }
        if (covered(message.topic())) {
            listModel.addElement(message);
            updateCount();
            if (follow.isSelected()) {
                int last = listModel.size() - 1;
                messageList.setSelectedIndex(last);
                messageList.ensureIndexIsVisible(last);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static java.awt.Font monospaced() {
        return new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12);
    }

    /** Tree node payload: the segment label plus the full topic path it represents. */
    private record TopicNode(String segment, String fullPath) {
        @Override
        public String toString() {
            return segment;
        }
    }

    /** Renders a raw message as "HH:mm:ss.SSS  topic  preview" (topic shown when aggregating). */
    private final class MessageRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RawMessage m) {
                StringBuilder sb = new StringBuilder();
                sb.append(TIME.format(Instant.ofEpochMilli(m.ts())));
                if (!selectedPath.equals(m.topic())) {
                    sb.append("  ").append(lastSegment(m.topic()));
                }
                if (m.retained()) {
                    sb.append("  [R]");
                }
                sb.append("  ").append(preview(m.payload()));
                setText(sb.toString());
                setFont(monospaced());
            }
            return this;
        }

        private String lastSegment(String topic) {
            int slash = topic.lastIndexOf('/');
            return slash < 0 ? topic : "…/" + topic.substring(slash + 1);
        }

        private String preview(String payload) {
            String s = payload == null ? "" : payload.replaceAll("\\s+", " ").trim();
            return s.length() > 80 ? s.substring(0, 80) + "…" : s;
        }
    }
}
