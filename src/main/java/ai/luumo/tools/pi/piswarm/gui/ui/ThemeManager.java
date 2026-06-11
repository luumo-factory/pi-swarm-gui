package ai.luumo.tools.pi.piswarm.gui.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Window;

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

    public Theme getTheme() {
        return theme;
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
        UIManager.put("InternalFrame.borderMargins", new Insets(0, 0, 0, 0));

        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
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
