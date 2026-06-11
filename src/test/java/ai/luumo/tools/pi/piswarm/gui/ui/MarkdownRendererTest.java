package ai.luumo.tools.pi.piswarm.gui.ui;

import org.junit.jupiter.api.Test;

import javax.swing.text.StyledDocument;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownRendererTest {

    private static String render(String markdown) throws Exception {
        PiOutputPane pane = new PiOutputPane();
        new MarkdownRenderer(new ThemeManager(), pane).render(markdown);
        Field f = PiOutputPane.class.getDeclaredField("pane");
        f.setAccessible(true);
        StyledDocument doc = ((javax.swing.JTextPane) f.get(pane)).getStyledDocument();
        return doc.getText(0, doc.getLength());
    }

    @Test
    void stripsInlineMarkersFromVisibleText() throws Exception {
        String text = render("This is **bold**, *italic*, `code` and ~~gone~~.");
        assertTrue(text.contains("bold"));
        assertTrue(text.contains("italic"));
        assertTrue(text.contains("code"));
        assertFalse(text.contains("**"));
        assertFalse(text.contains("~~"));
        assertFalse(text.contains("`"));
    }

    @Test
    void rendersHeadingsListsAndLinks() throws Exception {
        String text = render("# Title\n\n- item one\n- item two\n\n[pi](https://example.com)");
        assertTrue(text.contains("Title"));
        assertFalse(text.contains("#"));
        assertTrue(text.contains("• item one"));
        assertTrue(text.contains("• item two"));
        // Link label is shown, URL is hidden.
        assertTrue(text.contains("pi"));
        assertFalse(text.contains("https://example.com"));
    }

    @Test
    void preservesFencedCodeVerbatim() throws Exception {
        String text = render("```java\nint x = 1; // **not bold**\n```");
        assertTrue(text.contains("int x = 1; // **not bold**"));
        assertFalse(text.contains("```"));
    }
}
