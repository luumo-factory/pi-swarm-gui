package ai.luumo.tools.pi.piswarm.gui.ui;

import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A button that must be pressed and held to trigger. While held, a progress bar
 * sweeps across the button face; once it fills (after {@code holdMillis}) the
 * supplied action fires. Releasing early (or dragging the cursor off the button)
 * cancels and rewinds the progress.
 */
public final class HoldButton extends JButton {

    private static final int TICK_MS = 16; // ~60fps

    private final int holdMillis;
    private final Runnable onComplete;
    private final Timer timer;
    private Color fillColor = new Color(0xC0, 0x39, 0x2B); // danger red

    private long startNanos;
    private double progress; // 0..1
    private boolean armed;

    public HoldButton(String text, int holdMillis, Runnable onComplete) {
        super(text);
        this.holdMillis = Math.max(1, holdMillis);
        this.onComplete = onComplete;
        setFocusPainted(false);

        this.timer = new Timer(TICK_MS, e -> tick());
        this.timer.setCoalesce(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled() && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    start();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                cancel();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                cancel();
            }
        };
        addMouseListener(mouse);
    }

    /** Override the progress-fill colour (defaults to a danger red). */
    public void setFillColor(Color color) {
        if (color != null) {
            this.fillColor = color;
        }
    }

    private void start() {
        armed = true;
        startNanos = System.nanoTime();
        progress = 0;
        timer.start();
    }

    private void cancel() {
        if (!armed && progress == 0) {
            return;
        }
        armed = false;
        timer.stop();
        progress = 0;
        repaint();
    }

    private void tick() {
        if (!armed) {
            return;
        }
        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        progress = Math.min(1.0, elapsedMs / holdMillis);
        repaint();
        if (progress >= 1.0) {
            armed = false;
            timer.stop();
            progress = 0;
            repaint();
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (progress <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = (int) Math.round(getWidth() * progress);
            g2.setColor(new Color(fillColor.getRed(), fillColor.getGreen(),
                    fillColor.getBlue(), 130));
            g2.fillRect(0, 0, w, getHeight());
        } finally {
            g2.dispose();
        }
    }
}
