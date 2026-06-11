package ai.luumo.tools.pi.piswarm.gui.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JInternalFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the FlatLaf light/dark look and feel and exposes the accent colours
 * used by the pi-style output renderer so they track the active theme.
 */
public final class ThemeManager {

    public enum Theme {
        LIGHT, DARK;

        public static Theme from(String raw) {
            return "light".equalsIgnoreCase(raw) ? LIGHT : DARK;
        }
    }

    private Theme theme = Theme.DARK;

    /** Callbacks run after every theme change so windows can re-apply derived styling. */
    private final List<Runnable> themeListeners = new CopyOnWriteArrayList<>();

    public Theme getTheme() {
        return theme;
    }

    /** Register a callback fired (on the EDT) after each {@link #apply} completes. */
    public void addThemeListener(Runnable listener) {
        themeListeners.add(listener);
    }

    public void removeThemeListener(Runnable listener) {
        themeListeners.remove(listener);
    }

    public boolean isDark() {
        return theme == Theme.DARK;
    }

    /** Apply the given theme to the running application and repaint all windows. */
    public void apply(Theme newTheme) {
        this.theme = newTheme;
        FlatLaf laf = newTheme == Theme.DARK ? new FlatDarkLaf() : new FlatLightLaf();
        FlatLaf.setup(laf);

        // FlatLaf reserves a margin around each internal frame to paint a drop
        // shadow (InternalFrame.borderMargins / dropShadowPainted). That margin
        // makes snapped frames look like they have a gap even though their bounds
        // are flush, so drop the shadow and zero the margin. FlatLaf.setup() resets
        // UIManager defaults, so re-apply after every theme change.
        UIManager.put("InternalFrame.dropShadowPainted", Boolean.FALSE);
        // Reserve exactly the 1px line width (not the default 6px shadow margin):
        // 0 insets would let an opaque content pane paint over the border and hide
        // it entirely (board/debug), while 1px keeps the line visible and still
        // lets snapped frames sit flush (a 1px border each, no empty gap).
        UIManager.put("InternalFrame.borderMargins", new Insets(1, 1, 1, 1));
        // With the shadow gone the remaining 1px line border (borderLineWidth=1)
        // is the only edge cue, but its default colour is a near-background shade
        // and effectively invisible. Tint it a distinguishable purple so window
        // edges (and the seam between snapped frames) read clearly.
        boolean dark = newTheme == Theme.DARK;
        UIManager.put("InternalFrame.activeBorderColor", dark ? new Color(0x9A6BFF) : new Color(0x7B3FD1));
        UIManager.put("InternalFrame.inactiveBorderColor", dark ? new Color(0x5A4A7A) : new Color(0xB7A6DD));

        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }

        // updateComponentTreeUI reinstalls UIManager defaults, so any derived
        // sub-window tint has to be re-applied afterwards.
        for (Runnable listener : themeListeners) {
            listener.run();
        }
    }

    // ------------------------------------------------------------------
    // Sub-window tinting
    // ------------------------------------------------------------------

    /**
     * Background used for the content of secondary windows (agent monitors, the
     * message board, the debug view, control dialogs) so they read as a distinct,
     * slightly darker surface than the main application background.
     */
    public Color subWindowBackground() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            base = isDark() ? new Color(0x2b2b2b) : new Color(0xf2f2f2);
        }
        double factor = isDark() ? 0.82 : 0.92;
        return new Color(
                (int) Math.round(base.getRed() * factor),
                (int) Math.round(base.getGreen() * factor),
                (int) Math.round(base.getBlue() * factor));
    }

    /**
     * Recursively tint a sub-window's content with {@link #subWindowBackground()}
     * so its panels, toolbars, scroll panes and read-only feeds share one darker
     * surface. Editable inputs, buttons and other controls keep their own colours.
     */
    public void styleSubWindow(Component root) {
        applyBackground(root, subWindowBackground());
    }

    private static void applyBackground(Component c, Color bg) {
        if (shouldTint(c)) {
            c.setBackground(bg);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyBackground(child, bg);
            }
        }
    }

    private static boolean shouldTint(Component c) {
        if (c instanceof JTextComponent text) {
            // Tint read-only feeds (board/monitor/debug); leave editable inputs alone.
            return !text.isEditable();
        }
        return c instanceof JPanel
                || c instanceof JToolBar
                || c instanceof JSplitPane
                || c instanceof JScrollPane
                || c instanceof JViewport
                || c instanceof JList
                || c instanceof JTree
                || c instanceof JInternalFrame;
    }

    public void toggle() {
        apply(theme == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    // ------------------------------------------------------------------
    // Palette used by the pi-style renderer (resolved from the active LaF).
    // ------------------------------------------------------------------

    public Color foreground() {
        return UIManager.getColor("TextPane.foreground");
    }

    public Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : (isDark() ? new Color(0x9aa0a6) : new Color(0x70757a));
    }

    public Color assistant() {
        return foreground();
    }

    public Color userMessage() {
        return isDark() ? new Color(0x6cb6ff) : new Color(0x0b5fbf);
    }

    public Color tool() {
        return isDark() ? new Color(0xc191ff) : new Color(0x7b3fd1);
    }

    public Color success() {
        return isDark() ? new Color(0x6bd968) : new Color(0x2e8b2e);
    }

    public Color warning() {
        return isDark() ? new Color(0xffb454) : new Color(0xb5740a);
    }

    public Color error() {
        return isDark() ? new Color(0xff6b6b) : new Color(0xc0392b);
    }

    public Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : userMessage();
    }

    // ------------------------------------------------------------------
    // Markdown styling
    // ------------------------------------------------------------------

    public Color heading() {
        return accent();
    }

    public Color link() {
        return userMessage();
    }

    public Color quote() {
        return muted();
    }

    public Color codeForeground() {
        return isDark() ? new Color(0xe6db74) : new Color(0xa6390f);
    }

    public Color codeBackground() {
        return isDark() ? new Color(0x2b2f36) : new Color(0xeef0f3);
    }
}
