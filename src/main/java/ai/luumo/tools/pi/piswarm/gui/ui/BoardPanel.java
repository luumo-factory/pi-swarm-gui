package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.AgentGroup;
import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

/**
 * A window showing one colour-coded group's message board, with an input box to
 * broadcast posts to that group.
 */
public final class BoardPanel extends JPanel implements SwarmModel.SwarmModelListener {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SwarmModel model;
    private final ThemeManager theme;
    private final AgentGroup group;
    private final PiOutputPane output = new PiOutputPane();

    private final MessageInput input = new MessageInput();
    private final JCheckBox urgent = new JCheckBox("urgent");

    /** Callback: (text, urgent) -> publish to this group's board. */
    private final BiConsumer<String, Boolean> onPost;

    /** Callback: clear this group's board history (local + MQTT tombstone). */
    private final Runnable onClear;

    public BoardPanel(AgentGroup group, SwarmModel model, ThemeManager theme,
                      BiConsumer<String, Boolean> onPost, Runnable onClear) {
        super(new BorderLayout());
        this.model = model;
        this.theme = theme;
        this.group = group;
        this.onPost = onPost;
        this.onClear = onClear;

        add(buildHeader(), BorderLayout.NORTH);
        add(output, BorderLayout.CENTER);
        add(buildInput(), BorderLayout.SOUTH);

        model.addListener(this);
        for (BoardPost post : model.boardPosts(group)) {
            renderPost(post);
        }
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel icon = new JLabel(new ChainIcon(16, group.color()));
        JLabel title = new JLabel(group.label() + " message board");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        left.add(icon);
        left.add(title);
        header.add(left, BorderLayout.WEST);

        JButton clear = new JButton("Clear");
        clear.setToolTipText("Clear this board's history (and any retained message at the broker)");
        clear.addActionListener(e -> confirmClear());
        header.add(clear, BorderLayout.EAST);
        return header;
    }

    private void confirmClear() {
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Clear the " + group.label() + " board history?\n\n"
                        + "This wipes this window's history and publishes an empty\n"
                        + "retained message to clear any board state held at the broker.\n"
                        + "Board posts are not retained per-message, so other already-\n"
                        + "connected clients keep the history they have already received.",
                "Clear board", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice == javax.swing.JOptionPane.OK_OPTION) {
            onClear.run();
        }
    }

    private JPanel buildInput() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JButton post = new JButton("Post");
        post.addActionListener(e -> submit());
        input.setOnSubmit(this::submit);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(urgent);
        right.add(post);

        panel.add(new JLabel("Broadcast: "), BorderLayout.WEST);
        panel.add(input, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        onPost.accept(text, urgent.isSelected());
        input.setText("");
        urgent.setSelected(false);
    }

    private void renderPost(BoardPost post) {
        output.append(TIME.format(Instant.ofEpochMilli(post.ts())) + "  ", theme.muted());
        output.append("<" + post.fromName() + "> ", theme.accent(), true);
        if (post.urgent()) {
            output.append("[URGENT] ", theme.error(), true);
        }
        output.append(post.text(), theme.foreground());
        output.newline();
    }

    /** Move keyboard focus to the broadcast input box. */
    public void focusInput() {
        input.focusInput();
    }

    @Override
    public void boardPost(BoardPost post) {
        if (post.groupOrDefault() == group) {
            renderPost(post);
        }
    }

    @Override
    public void boardCleared(AgentGroup cleared) {
        if (cleared == group) {
            output.clearOutput();
        }
    }
}
