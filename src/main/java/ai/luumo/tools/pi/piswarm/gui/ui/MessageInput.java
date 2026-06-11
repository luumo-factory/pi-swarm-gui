package ai.luumo.tools.pi.piswarm.gui.ui;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * A compact, auto-growing message input box.
 *
 * <ul>
 *   <li><b>Enter</b> submits (fires the {@link #setOnSubmit submit} callback).</li>
 *   <li><b>Shift+Enter</b> inserts a newline.</li>
 *   <li>The box grows up to three visible rows whenever the text spans multiple
 *       lines &mdash; whether from explicit newlines <em>or</em> soft word-wrap
 *       &mdash; and shrinks back to a single row when it returns to one line.</li>
 * </ul>
 *
 * <p>Exposes {@link #getText()} / {@link #setText(String)} so it is a drop-in
 * replacement for the single-line {@code JTextField} it supersedes.</p>
 */
public final class MessageInput extends JScrollPane {

    private static final int MAX_ROWS = 3;

    private final JTextArea area = new JTextArea(1, 10);
    private Runnable onSubmit = () -> { };
    private int rows = 1;

    public MessageInput() {
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);

        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(1);
        setViewportView(area);

        installKeys();
        installAutoResize();
    }

    /** Set the callback invoked when the user presses Enter. */
    public void setOnSubmit(Runnable onSubmit) {
        this.onSubmit = (onSubmit == null) ? () -> { } : onSubmit;
    }

    public String getText() {
        return area.getText();
    }

    public void setText(String text) {
        area.setText(text);
    }

    /** Move keyboard focus into the text area. */
    public void focusInput() {
        area.requestFocusInWindow();
    }

    /** Visible for testing: the backing text area. */
    JTextArea textArea() {
        return area;
    }

    /** Visible for testing: number of rows currently shown (1..{@value #MAX_ROWS}). */
    int currentRows() {
        return rows;
    }

    /**
     * Height tracks the current row count directly. A wrapping {@code JTextArea}
     * inside a scroll pane doesn't reliably grow the pane's preferred height on
     * its own, so we size it ourselves to exactly {@code rows} lines.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        FontMetrics fm = area.getFontMetrics(area.getFont());
        Insets ai = area.getInsets();
        Insets si = getInsets();
        d.height = rows * fm.getHeight() + ai.top + ai.bottom + si.top + si.bottom;
        return d;
    }

    private void installKeys() {
        InputMap im = area.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = area.getActionMap();

        // Enter submits; Shift+Enter falls back to the editor's default
        // "insert-break" action so it inserts a newline instead.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit-message");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");
        am.put("submit-message", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSubmit.run();
            }
        });

        // Let Ctrl+Arrow bubble up to the window-level frame navigation rather
        // than being swallowed as caret word-movement inside the text area.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "none");
    }

    private void installAutoResize() {
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleAdjust();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleAdjust();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleAdjust();
            }
        });
        // Wrapping depends on the available width, so re-measure when it changes.
        area.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjust();
            }
        });
    }

    /**
     * Re-measure after the document edit has been applied to the text view.
     * Measuring synchronously inside the document listener reads the view's
     * pre-edit wrap layout, which makes growth lag a keystroke behind.
     */
    private void scheduleAdjust() {
        SwingUtilities.invokeLater(this::adjust);
    }

    /** Grow to fit (capped at {@link #MAX_ROWS}) and shrink back as text changes. */
    void adjust() {
        int wanted = measureRows();
        if (wanted == rows) {
            return;
        }
        rows = wanted;

        // A JScrollPane is itself a validate root, so revalidate()/invalidate()
        // called on us would stop here and never re-lay-out the parent that
        // controls our height. Start from the parent instead.
        Container parent = getParent();
        if (parent == null) {
            invalidate();
            validate();
            repaint();
            return;
        }
        if (parent instanceof JComponent jc) {
            jc.revalidate(); // queue a layout up to the parent's validate root
        } else {
            parent.invalidate();
        }
        // Validate the enclosing validate root synchronously so the box resizes
        // immediately rather than only on the next forced layout (e.g. a drag).
        Container root = parent;
        while (root != null && !root.isValidateRoot()) {
            root = root.getParent();
        }
        if (root != null) {
            root.validate();
        }
        repaint();
    }

    /** Number of display rows the current text needs, counting soft wraps. */
    private int measureRows() {
        int lineHeight = area.getFontMetrics(area.getFont()).getHeight();
        int lines;
        if (area.getWidth() <= 0 || lineHeight <= 0) {
            // Not laid out yet: fall back to counting explicit newlines.
            lines = area.getLineCount();
        } else {
            Insets ai = area.getInsets();
            int contentHeight = area.getPreferredSize().height - ai.top - ai.bottom;
            lines = Math.round(contentHeight / (float) lineHeight);
        }
        return Math.max(1, Math.min(MAX_ROWS, lines));
    }
}
