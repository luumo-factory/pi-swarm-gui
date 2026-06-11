package ai.luumo.tools.pi.piswarm.gui.ui;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;

/**
 * A read-only, auto-scrolling styled text area used to render a "pi-style"
 * terminal feed. Append coloured/styled segments; the view keeps a monospaced
 * font and scrolls to the bottom as content arrives.
 */
public final class PiOutputPane extends JScrollPane {

    private final JTextPane pane = new JTextPane();
    private boolean autoScroll = true;

    public PiOutputPane() {
        pane.setEditable(false);
        pane.setFont(monospaced());
        setViewportView(pane);
        getVerticalScrollBar().setUnitIncrement(16);
    }

    /**
     * Keep only a top/bottom rule on the feed. The hosting messaging windows put
     * the output box flush against their own left/right edges, so a full box
     * border would double up with the window border there; a horizontal rule is
     * enough to separate the feed from the controls above and below it. Re-applied
     * here (not in the constructor) so it survives look-and-feel/theme changes,
     * which reinstall the scroll pane's default border via updateUI().
     */
    @Override
    public void updateUI() {
        super.updateUI();
        Color line = UIManager.getColor("Component.borderColor");
        if (line == null) {
            line = UIManager.getColor("Separator.foreground");
        }
        if (line == null) {
            line = Color.GRAY;
        }
        setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, line));
    }

    private static Font monospaced() {
        // Prefer common dev fonts; fall back to the logical monospaced family.
        for (String family : new String[] {"JetBrains Mono", "Fira Code", "DejaVu Sans Mono", "Menlo", "Consolas"}) {
            Font f = new Font(family, Font.PLAIN, 13);
            if (f.getFamily().equalsIgnoreCase(family)) {
                return f;
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 13);
    }

    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }

    public boolean isAutoScroll() {
        return autoScroll;
    }

    public void clearOutput() {
        pane.setText("");
    }

    /** Append a plain segment in the default foreground colour. */
    public void append(String text) {
        append(text, null, false, false);
    }

    /** Append a coloured segment. */
    public void append(String text, Color color) {
        append(text, color, false, false);
    }

    public void append(String text, Color color, boolean bold) {
        append(text, color, bold, false);
    }

    public void append(String text, Color color, boolean bold, boolean italic) {
        appendStyled(text, color, null, bold, italic, false, false);
    }

    /**
     * Append a fully styled segment. {@code fg}/{@code bg} may be {@code null}
     * to inherit the pane defaults.
     */
    public void appendStyled(String text, Color fg, Color bg,
                             boolean bold, boolean italic, boolean underline, boolean strike) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        if (fg != null) {
            StyleConstants.setForeground(attrs, fg);
        }
        if (bg != null) {
            StyleConstants.setBackground(attrs, bg);
        }
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setItalic(attrs, italic);
        StyleConstants.setUnderline(attrs, underline);
        StyleConstants.setStrikeThrough(attrs, strike);
        StyledDocument doc = pane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException e) {
            // unreachable for append-at-end
        }
        if (autoScroll) {
            pane.setCaretPosition(doc.getLength());
        }
    }

    public void newline() {
        append("\n");
    }
}
