package ai.luumo.tools.pi.piswarm.gui.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnappingDesktopManagerTest {

    private static final int TOL = SnappingDesktopManager.REFLOW_TOLERANCE;

    @Test
    void draggingSnapsAdjacentFramesToOverlapBordersByOnePixel() {
        JDesktopPane desktop = new JDesktopPane();
        desktop.setSize(1000, 600);
        SnappingDesktopManager mgr = new SnappingDesktopManager();
        desktop.setDesktopManager(mgr);

        JInternalFrame a = new JInternalFrame("a", true, true, true, true);
        a.setBounds(0, 0, 200, 400);
        a.setVisible(true);
        desktop.add(a);
        JInternalFrame b = new JInternalFrame("b", true, true, true, true);
        b.setBounds(500, 0, 200, 400);
        b.setVisible(true);
        desktop.add(b);

        // Drag b so its left edge is near a's right edge (200): it should snap to
        // 199 (a.right - 1) so the two 1px borders overlap into one line.
        mgr.dragFrame(b, 196, 0);
        assertEquals(199, b.getX(), "left edge should overlap neighbour by the border width");
        assertEquals(399, b.getX() + b.getWidth());

        // Drag b's left edge near the desktop's left edge: snap flush to 0 (no
        // overlap against the desktop).
        mgr.dragFrame(b, 3, 0);
        assertEquals(0, b.getX());
    }

    @Test
    void tiledRowStretchesProportionally() {
        // 20:60:20 row spanning the full 1000px width, full height 400.
        Rectangle left = new Rectangle(0, 0, 200, 400);
        Rectangle mid = new Rectangle(200, 0, 600, 400);
        Rectangle right = new Rectangle(800, 0, 200, 400);

        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{left, mid, right}, 1000, 400, 1010, 400, TOL);

        // +10px width distributed 2 / 6 / 2.
        assertEquals(new Rectangle(0, 0, 202, 400), out[0]);
        assertEquals(new Rectangle(202, 0, 606, 400), out[1]);
        assertEquals(new Rectangle(808, 0, 202, 400), out[2]);
        // Stay flush, ends pinned to the desktop edges.
        assertEquals(out[0].x + out[0].width, out[1].x);
        assertEquals(out[1].x + out[1].width, out[2].x);
        assertEquals(1010, out[2].x + out[2].width);
    }

    @Test
    void verticalColumnStretchesProportionally() {
        Rectangle top = new Rectangle(0, 0, 300, 200);
        Rectangle bottom = new Rectangle(0, 200, 300, 800);

        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{top, bottom}, 300, 1000, 300, 1010, TOL);

        assertEquals(new Rectangle(0, 0, 300, 202), out[0]);
        assertEquals(new Rectangle(0, 202, 300, 808), out[1]);
    }

    @Test
    void floatingWindowIsUntouched() {
        Rectangle floating = new Rectangle(100, 100, 300, 200); // touches no edge
        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{floating}, 1000, 1000, 1200, 1100, TOL);
        assertEquals(floating, out[0]);
    }

    @Test
    void leftDockedOnlyKeepsSize() {
        // Snapped to the left edge but right edge free => should not change on a
        // horizontal resize.
        Rectangle w = new Rectangle(0, 0, 300, 400);
        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{w}, 1000, 400, 1010, 400, TOL);
        assertEquals(w, out[0]);
    }

    @Test
    void rightDockedOnlyTranslates() {
        // Snapped to the right edge, left edge free => follows the edge keeping size.
        Rectangle w = new Rectangle(700, 0, 300, 400); // right == 1000
        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{w}, 1000, 400, 1010, 400, TOL);
        assertEquals(new Rectangle(710, 0, 300, 400), out[0]);
    }

    @Test
    void shrinkDistributesProportionally() {
        Rectangle left = new Rectangle(0, 0, 200, 400);
        Rectangle mid = new Rectangle(200, 0, 600, 400);
        Rectangle right = new Rectangle(800, 0, 200, 400);

        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{left, mid, right}, 1000, 400, 900, 400, TOL);

        assertEquals(new Rectangle(0, 0, 180, 400), out[0]);
        assertEquals(new Rectangle(180, 0, 540, 400), out[1]);
        assertEquals(new Rectangle(720, 0, 180, 400), out[2]);
        assertEquals(900, out[2].x + out[2].width);
    }

    @Test
    void separateRowsScaleIndependentlyOnlyOnResizedAxis() {
        // A 2x2 grid spanning 1000x1000; resize width only by +20.
        Rectangle tl = new Rectangle(0, 0, 400, 500);
        Rectangle tr = new Rectangle(400, 0, 600, 500);
        Rectangle bl = new Rectangle(0, 500, 400, 500);
        Rectangle br = new Rectangle(400, 500, 600, 500);

        Rectangle[] out = SnappingDesktopManager.reflow(
                new Rectangle[]{tl, tr, bl, br}, 1000, 1000, 1020, 1000, TOL);

        // Each row splits the +20 as 40%/60% => +8 / +12; heights unchanged.
        assertEquals(new Rectangle(0, 0, 408, 500), out[0]);
        assertEquals(new Rectangle(408, 0, 612, 500), out[1]);
        assertEquals(new Rectangle(0, 500, 408, 500), out[2]);
        assertEquals(new Rectangle(408, 500, 612, 500), out[3]);
    }
}
