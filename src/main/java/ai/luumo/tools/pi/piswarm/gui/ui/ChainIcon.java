package ai.luumo.tools.pi.piswarm.gui.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.RoundRectangle2D;

/**
 * A small square chain/link icon painted in a group's colour. Used to designate
 * an agent's (or a board's) group throughout the UI.
 */
public final class ChainIcon implements Icon {

    private final int size;
    private Color color;

    public ChainIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        float stroke = Math.max(1.4f, size / 8f);
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Two interlocking rounded links drawn on a diagonal, evoking a chain.
        float linkW = size * 0.58f;
        float linkH = size * 0.40f;
        float arc = linkH;
        g2.draw(new RoundRectangle2D.Float(
                x + size * 0.04f, y + size * 0.10f, linkW, linkH, arc, arc));
        g2.draw(new RoundRectangle2D.Float(
                x + size - linkW - size * 0.04f, y + size * 0.50f, linkW, linkH, arc, arc));

        g2.setStroke(old);
        g2.dispose();
    }
}
