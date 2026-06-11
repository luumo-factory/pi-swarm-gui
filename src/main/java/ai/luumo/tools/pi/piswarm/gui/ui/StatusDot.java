package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.AgentStatus;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A small filled circle indicating an agent's status, shared by the agent list
 * and the monitor window header.
 */
public final class StatusDot extends JComponent {

    private Color color = Color.GRAY;

    public StatusDot() {
        setPreferredSize(new Dimension(14, 14));
    }

    public void setColor(Color color) {
        this.color = color == null ? Color.GRAY : color;
    }

    /** The status colour for a given status under the active theme. */
    public static Color colorFor(AgentStatus status, ThemeManager theme) {
        return switch (status) {
            case BUSY -> theme.warning();
            case IDLE, ONLINE -> theme.success();
            case OFFLINE -> theme.error();
            case UNKNOWN -> theme.muted();
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int d = 10;
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;
        g2.setColor(color);
        g2.fillOval(x, y, d, d);
        g2.dispose();
    }
}
