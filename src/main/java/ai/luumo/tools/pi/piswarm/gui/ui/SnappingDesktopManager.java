package ai.luumo.tools.pi.piswarm.gui.ui;

import javax.swing.DefaultDesktopManager;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link javax.swing.DesktopManager} that makes internal frames snap to one
 * another and to the edges of the surrounding desktop while they are dragged or
 * resized. Snapping engages when an edge of the moving frame comes within
 * {@link #snapDistance} pixels of a candidate edge.
 */
public final class SnappingDesktopManager extends DefaultDesktopManager {

    /** Default snapping distance, in pixels. */
    public static final int DEFAULT_SNAP_DISTANCE = 12;

    /**
     * Tolerance (px) for deciding whether a frame edge is flush with another
     * frame or with a desktop edge when reflowing on a desktop resize. Snapped
     * frames are exactly flush, so this only needs to absorb rounding.
     */
    public static final int REFLOW_TOLERANCE = 2;

    /**
     * Pixels two frames overlap when snapped edge-to-edge, equal to the frame
     * border line width. Overlapping by exactly the border collapses the two
     * adjacent 1px borders onto the same pixels so the seam reads as a single
     * line instead of a doubled one. Snapping to a desktop edge does not overlap.
     */
    public static final int BORDER_OVERLAP = 1;

    private final int snapDistance;

    public SnappingDesktopManager() {
        this(DEFAULT_SNAP_DISTANCE);
    }

    public SnappingDesktopManager(int snapDistance) {
        this.snapDistance = Math.max(0, snapDistance);
    }

    @Override
    public void dragFrame(JComponent f, int newX, int newY) {
        if (snapDistance > 0 && f instanceof JInternalFrame frame) {
            int w = frame.getWidth();
            int h = frame.getHeight();
            newX = snapSpan(newX, w, nearTargets(frame, true), farTargets(frame, true));
            newY = snapSpan(newY, h, nearTargets(frame, false), farTargets(frame, false));
        }
        super.dragFrame(f, newX, newY);
    }

    @Override
    public void resizeFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
        if (snapDistance > 0 && f instanceof JInternalFrame frame) {
            int right = newX + newWidth;
            int bottom = newY + newHeight;
            int[] nearX = nearTargets(frame, true);
            int[] farX = farTargets(frame, true);
            int[] nearY = nearTargets(frame, false);
            int[] farY = farTargets(frame, false);

            // Snap the moving (left/top) edges to where a near edge may sit.
            int snappedX = snapEdge(newX, nearX);
            int snappedY = snapEdge(newY, nearY);
            if (snappedX != newX) {
                newWidth += newX - snappedX;
                newX = snappedX;
            }
            if (snappedY != newY) {
                newHeight += newY - snappedY;
                newY = snappedY;
            }

            // Snap the far (right/bottom) edges to where a far edge may sit.
            int snappedRight = snapEdge(right, farX);
            int snappedBottom = snapEdge(bottom, farY);
            if (snappedRight != right) {
                newWidth = snappedRight - newX;
            }
            if (snappedBottom != bottom) {
                newHeight = snappedBottom - newY;
            }
        }
        super.resizeFrame(f, newX, newY, newWidth, newHeight);
    }

    // ------------------------------------------------------------------
    // Snap helpers
    // ------------------------------------------------------------------

    /**
     * Snaps a frame whose near edge is at {@code pos} (extent {@code size}) to the
     * closest of its near-edge targets or far-edge targets; returns the adjusted
     * near-edge position.
     */
    private int snapSpan(int pos, int size, int[] nearTargets, int[] farTargets) {
        int best = pos;
        int bestDist = snapDistance + 1;
        for (int t : nearTargets) {
            int d = Math.abs(t - pos);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        for (int t : farTargets) {
            int d = Math.abs(t - (pos + size));
            if (d < bestDist) {
                bestDist = d;
                best = t - size;
            }
        }
        return best;
    }

    /** Snaps a single edge position to the nearest candidate target. */
    private int snapEdge(int pos, int[] targets) {
        int best = pos;
        int bestDist = snapDistance + 1;
        for (int t : targets) {
            int d = Math.abs(t - pos);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    /**
     * Positions the moving frame's near (left/top) edge may snap to: the desktop's
     * near edge (flush), another frame's matching near edge (aligned, borders
     * already coincide), or another frame's far edge minus the border overlap
     * (adjacency — the two 1px borders collapse onto one line).
     */
    private int[] nearTargets(JInternalFrame frame, boolean horizontal) {
        List<Integer> targets = new ArrayList<>();
        JDesktopPane desktop = frame.getDesktopPane();
        if (desktop != null) {
            targets.add(0);
            for (JInternalFrame other : desktop.getAllFrames()) {
                if (other == frame || !other.isVisible() || other.isIcon()) {
                    continue;
                }
                int near = horizontal ? other.getX() : other.getY();
                int far = near + (horizontal ? other.getWidth() : other.getHeight());
                targets.add(near);
                targets.add(far - BORDER_OVERLAP);
            }
        }
        return toArray(targets);
    }

    /**
     * Positions the moving frame's far (right/bottom) edge may snap to: the
     * desktop's far edge (flush), another frame's matching far edge (aligned), or
     * another frame's near edge plus the border overlap (adjacency).
     */
    private int[] farTargets(JInternalFrame frame, boolean horizontal) {
        List<Integer> targets = new ArrayList<>();
        JDesktopPane desktop = frame.getDesktopPane();
        if (desktop != null) {
            targets.add(horizontal ? desktop.getWidth() : desktop.getHeight());
            for (JInternalFrame other : desktop.getAllFrames()) {
                if (other == frame || !other.isVisible() || other.isIcon()) {
                    continue;
                }
                int near = horizontal ? other.getX() : other.getY();
                int far = near + (horizontal ? other.getWidth() : other.getHeight());
                targets.add(far);
                targets.add(near + BORDER_OVERLAP);
            }
        }
        return toArray(targets);
    }

    // ------------------------------------------------------------------
    // Reflow on desktop resize
    // ------------------------------------------------------------------

    /**
     * Re-lay-out the desktop's frames after it has been resized from
     * {@code old} to {@code new} dimensions. Frames whose edges are snapped to a
     * desktop edge (directly or through a flush chain of neighbours) follow that
     * edge: a frame anchored on both sides of an axis stretches proportionally,
     * one anchored only to the far edge translates, and otherwise it is left
     * untouched. Maximized / iconified frames are skipped.
     */
    public void desktopResized(JDesktopPane desktop, int oldW, int oldH, int newW, int newH) {
        if (desktop == null || (oldW == newW && oldH == newH)) {
            return;
        }
        List<JInternalFrame> frames = new ArrayList<>();
        for (JInternalFrame f : desktop.getAllFrames()) {
            if (f.isVisible() && !f.isIcon() && !f.isMaximum()) {
                frames.add(f);
            }
        }
        if (frames.isEmpty()) {
            return;
        }
        Rectangle[] before = new Rectangle[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            before[i] = frames.get(i).getBounds();
        }
        Rectangle[] after = reflow(before, oldW, oldH, newW, newH, REFLOW_TOLERANCE);
        for (int i = 0; i < frames.size(); i++) {
            if (!before[i].equals(after[i])) {
                frames.get(i).setBounds(after[i]);
            }
        }
    }

    /**
     * Pure geometry for {@link #desktopResized}: given the current frame bounds
     * and the old/new desktop size, returns the reflowed bounds. Each axis is
     * handled independently using edge anchoring relative to the <em>old</em>
     * desktop size.
     */
    public static Rectangle[] reflow(Rectangle[] bounds, int oldW, int oldH, int newW, int newH, int tol) {
        int n = bounds.length;
        int[] x = new int[n];
        int[] right = new int[n];
        int[] y = new int[n];
        int[] bottom = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = bounds[i].x;
            right[i] = bounds[i].x + bounds[i].width;
            y[i] = bounds[i].y;
            bottom[i] = bounds[i].y + bounds[i].height;
        }

        // Horizontal anchoring uses vertical overlap to identify real neighbours.
        boolean[] leftA = anchorNear(x, right, y, bottom, 0, tol);
        boolean[] rightA = anchorFar(x, right, y, bottom, oldW, tol);
        // Vertical anchoring uses horizontal overlap.
        boolean[] topA = anchorNear(y, bottom, x, right, 0, tol);
        boolean[] botA = anchorFar(y, bottom, x, right, oldH, tol);

        double fx = oldW > 0 ? (double) newW / oldW : 1.0;
        double fy = oldH > 0 ? (double) newH / oldH : 1.0;
        int dx = newW - oldW;
        int dy = newH - oldH;

        Rectangle[] out = new Rectangle[n];
        for (int i = 0; i < n; i++) {
            int[] xs = transform(x[i], right[i], leftA[i], rightA[i], fx, dx);
            int[] ys = transform(y[i], bottom[i], topA[i], botA[i], fy, dy);
            out[i] = new Rectangle(xs[0], ys[0], xs[1] - xs[0], ys[1] - ys[0]);
        }
        return out;
    }

    /** Map an axis span (near..far) given whether each edge is anchored to a desktop edge. */
    private static int[] transform(int near, int far, boolean nearAnchored, boolean farAnchored,
                                   double factor, int delta) {
        if (nearAnchored && farAnchored) {
            // Stretched between both edges: scale proportionally so shared
            // boundaries stay flush and the ends land exactly on the desktop edges.
            return new int[]{(int) Math.round(near * factor), (int) Math.round(far * factor)};
        }
        if (farAnchored) {
            // Docked to the far edge only: translate, preserving size.
            return new int[]{near + delta, far + delta};
        }
        // Docked to the near edge only, or free: leave untouched.
        return new int[]{near, far};
    }

    /**
     * Near-edge anchoring along one axis: a frame's near edge is anchored if it
     * sits on the desktop's near edge (position {@code edge}) or is flush with
     * the far edge of an anchored neighbour that overlaps it on the other axis.
     */
    private static boolean[] anchorNear(int[] near, int[] far, int[] pNear, int[] pFar, int edge, int tol) {
        int n = near.length;
        boolean[] anchored = new boolean[n];
        for (int i = 0; i < n; i++) {
            anchored[i] = Math.abs(near[i] - edge) <= tol;
        }
        propagate(anchored, far, near, pNear, pFar, tol);
        return anchored;
    }

    /**
     * Far-edge anchoring along one axis: a frame's far edge is anchored if it
     * sits on the desktop's far edge (position {@code edge}) or is flush with the
     * near edge of an anchored neighbour that overlaps it on the other axis.
     */
    private static boolean[] anchorFar(int[] near, int[] far, int[] pNear, int[] pFar, int edge, int tol) {
        int n = near.length;
        boolean[] anchored = new boolean[n];
        for (int i = 0; i < n; i++) {
            anchored[i] = Math.abs(far[i] - edge) <= tol;
        }
        propagate(anchored, near, far, pNear, pFar, tol);
        return anchored;
    }

    /**
     * Fixpoint propagation of anchoring across flush, overlapping neighbours.
     * {@code fromEdge} is the neighbour edge that must meet this frame's
     * {@code toEdge} (e.g. neighbour.far meets this.near for left anchoring).
     */
    private static void propagate(boolean[] anchored, int[] fromEdge, int[] toEdge,
                                  int[] pNear, int[] pFar, int tol) {
        int n = anchored.length;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                if (anchored[i]) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (j == i || !anchored[j]) {
                        continue;
                    }
                    if (Math.abs(fromEdge[j] - toEdge[i]) <= tol && overlaps(pNear, pFar, i, j)) {
                        anchored[i] = true;
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    /** Whether frames i and j overlap on the perpendicular axis. */
    private static boolean overlaps(int[] pNear, int[] pFar, int i, int j) {
        return pNear[i] < pFar[j] && pNear[j] < pFar[i];
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
