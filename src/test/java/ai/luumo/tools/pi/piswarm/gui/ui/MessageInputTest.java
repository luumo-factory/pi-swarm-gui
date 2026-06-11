package ai.luumo.tools.pi.piswarm.gui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MessageInputTest {

    @Test
    void singleLineStaysOneRow() {
        MessageInput input = new MessageInput();
        input.setText("hello world");
        input.adjust();
        assertEquals(1, input.currentRows());
    }

    @Test
    void growsWithNewlinesButCapsAtThreeRows() {
        MessageInput input = new MessageInput();

        input.setText("a\nb");
        input.adjust();
        assertEquals(2, input.currentRows());

        input.setText("a\nb\nc");
        input.adjust();
        assertEquals(3, input.currentRows());

        // More than three logical lines is clamped to three (then it scrolls).
        input.setText("a\nb\nc\nd\ne");
        input.adjust();
        assertEquals(3, input.currentRows());
    }

    @Test
    void growsOnSoftWrapWithoutNewlines() {
        MessageInput input = new MessageInput();
        // Give the text area a narrow, realised width so the UI computes wraps.
        input.textArea().setSize(40, 200);
        input.setText("the quick brown fox jumps over the lazy dog again and again");
        input.adjust();
        assertTrue(input.currentRows() > 1,
                "a long wrapping line (no newlines) should grow the box, got "
                        + input.currentRows() + " row(s)");
    }

    @Test
    void shrinksBackToOneRowWhenCleared() {
        MessageInput input = new MessageInput();
        input.setText("a\nb\nc");
        input.adjust();
        assertEquals(3, input.currentRows());

        input.setText("");
        input.adjust();
        assertEquals(1, input.currentRows());
    }

    @Test
    void enterFiresSubmitCallback() {
        MessageInput input = new MessageInput();
        boolean[] submitted = {false};
        input.setOnSubmit(() -> submitted[0] = true);

        Action submit = input.textArea().getActionMap().get("submit-message");
        assertNotNull(submit, "Enter should be bound to a submit action");
        submit.actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "submit"));

        assertTrue(submitted[0], "submit callback should run on Enter");
    }

    @Test
    void preferredHeightGrowsWithRowsThenShrinks() {
        MessageInput input = new MessageInput();
        int oneLine = input.getPreferredSize().height;

        input.setText("a\nb\nc");
        input.adjust();
        int threeLines = input.getPreferredSize().height;
        assertTrue(threeLines > oneLine,
                "input should be taller with three lines (" + threeLines + " > " + oneLine + ")");

        input.setText("");
        input.adjust();
        assertEquals(oneLine, input.getPreferredSize().height,
                "input should return to its single-line height when cleared");
    }

    @Test
    void growingInputInvalidatesParentAndGrowsItsPreferredHeight() throws Exception {
        // A JScrollPane is itself a validate root, so the growth must be pushed
        // onto the *parent*; otherwise it stops at the scroll pane and the box
        // only resizes on the next forced layout (e.g. dragging the window).
        MessageInput input = new MessageInput();
        int[] parentInvalidations = {0};
        JPanel inputPanel = new JPanel(new BorderLayout()) {
            @Override
            public void invalidate() {
                parentInvalidations[0]++;
                super.invalidate();
            }
        };
        inputPanel.add(new JLabel("Message: "), BorderLayout.WEST);
        inputPanel.add(input, BorderLayout.CENTER);
        // The panel needs a parent of its own, since JComponent.revalidate()
        // no-ops on a parentless component (as it would in the real frame).
        JPanel host = new JPanel(new BorderLayout());
        host.add(inputPanel, BorderLayout.SOUTH);

        int singleRowPanelHeight = inputPanel.getPreferredSize().height;

        // Run on the EDT, as in the live app, so revalidate() invalidates the
        // parent synchronously rather than merely queueing itself.
        SwingUtilities.invokeAndWait(() -> {
            parentInvalidations[0] = 0;
            input.setText("a\nb\nc");
            input.adjust();
        });

        assertTrue(parentInvalidations[0] > 0,
                "growing the input must invalidate its parent, not stop at the scroll pane");
        int grownPanelHeight = inputPanel.getPreferredSize().height;
        assertTrue(grownPanelHeight > singleRowPanelHeight,
                "the enclosing input panel's preferred height should grow ("
                        + grownPanelHeight + " > " + singleRowPanelHeight + ")");

        SwingUtilities.invokeAndWait(() -> {
            input.setText("");
            input.adjust();
        });
        assertEquals(singleRowPanelHeight, inputPanel.getPreferredSize().height,
                "the input panel should return to its single-row height when cleared");
    }

    @Test
    void getAndSetTextRoundTrip() {
        MessageInput input = new MessageInput();
        input.setText("line one\nline two");
        assertEquals("line one\nline two", input.getText());
    }
}
