package ai.luumo.tools.pi.piswarm.gui.ui;

import javax.swing.DefaultDesktopManager;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
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
            int[] xLines = collectXLines(frame);
            int[] yLines = collectYLines(frame);
            newX = snap(newX, w, xLines);
            newY = snap(newY, h, yLines);
        }
        super.dragFrame(f, newX, newY);
    }

    @Override
    public void resizeFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
        if (snapDistance > 0 && f instanceof JInternalFrame frame) {
            int right = newX + newWidth;
            int bottom = newY + newHeight;
            int[] xLines = collectXLines(frame);
            int[] yLines = collectYLines(frame);

            // Snap the moving (left/top) edges.
            int snappedX = snapEdge(newX, xLines);
            int snappedY = snapEdge(newY, yLines);
            if (snappedX != newX) {
                newWidth += newX - snappedX;
                newX = snappedX;
            }
            if (snappedY != newY) {
                newHeight += newY - snappedY;
                newY = snappedY;
            }

            // Snap the far (right/bottom) edges, adjusting size only.
            int snappedRight = snapEdge(right, xLines);
            int snappedBottom = snapEdge(bottom, yLines);
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
     * Snaps a frame whose near edge is at {@code pos} and whose extent is
     * {@code size} so that either its near edge or its far edge aligns with a
     * candidate line. Returns the adjusted near-edge position.
     */
    private int snap(int pos, int size, int[] lines) {
        int best = pos;
        int bestDist = snapDistance + 1;
        for (int line : lines) {
            int dNear = Math.abs(line - pos);
            if (dNear < bestDist) {
                bestDist = dNear;
                best = line;
            }
            int dFar = Math.abs(line - (pos + size));
            if (dFar < bestDist) {
                bestDist = dFar;
                best = line - size;
            }
        }
        return best;
    }

    /** Snaps a single edge position to the nearest candidate line. */
    private int snapEdge(int pos, int[] lines) {
        int best = pos;
        int bestDist = snapDistance + 1;
        for (int line : lines) {
            int d = Math.abs(line - pos);
            if (d < bestDist) {
                bestDist = d;
                best = line;
            }
        }
        return best;
    }

    private int[] collectXLines(JInternalFrame frame) {
        List<Integer> lines = new ArrayList<>();
        JDesktopPane desktop = frame.getDesktopPane();
        if (desktop != null) {
            lines.add(0);
            lines.add(desktop.getWidth());
            for (JInternalFrame other : desktop.getAllFrames()) {
                if (other == frame || !other.isVisible() || other.isIcon()) {
                    continue;
                }
                lines.add(other.getX());
                lines.add(other.getX() + other.getWidth());
            }
        }
        return toArray(lines);
    }

    private int[] collectYLines(JInternalFrame frame) {
        List<Integer> lines = new ArrayList<>();
        JDesktopPane desktop = frame.getDesktopPane();
        if (desktop != null) {
            lines.add(0);
            lines.add(desktop.getHeight());
            for (JInternalFrame other : desktop.getAllFrames()) {
                if (other == frame || !other.isVisible() || other.isIcon()) {
                    continue;
                }
                lines.add(other.getY());
                lines.add(other.getY() + other.getHeight());
            }
        }
        return toArray(lines);
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
