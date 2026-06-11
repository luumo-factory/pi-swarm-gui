package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.BoardPost;
import ai.luumo.tools.pi.piswarm.gui.swarm.SwarmModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

/**
 * Centre panel showing the shared swarm message board with an input box to
 * broadcast posts to every agent.
 */
public final class BoardPanel extends JPanel implements SwarmModel.SwarmModelListener {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SwarmModel model;
    private final ThemeManager theme;
    private final PiOutputPane output = new PiOutputPane();

    private final JTextField input = new JTextField();
    private final JCheckBox urgent = new JCheckBox("urgent");

    /** Callback: (text, urgent) -> publish to board. */
    private final BiConsumer<String, Boolean> onPost;

    public BoardPanel(SwarmModel model, ThemeManager theme, BiConsumer<String, Boolean> onPost) {
        super(new BorderLayout());
        this.model = model;
        this.theme = theme;
        this.onPost = onPost;

        JLabel header = new JLabel("Shared message board");
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        add(header, BorderLayout.NORTH);

        add(output, BorderLayout.CENTER);
        add(buildInput(), BorderLayout.SOUTH);

        model.addListener(this);
        for (BoardPost post : model.boardPosts()) {
            renderPost(post);
        }
    }

    private JPanel buildInput() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JButton post = new JButton("Post");
        post.addActionListener(e -> submit());
        input.addActionListener(e -> submit());

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

    @Override
    public void boardPost(BoardPost post) {
        renderPost(post);
    }
}
