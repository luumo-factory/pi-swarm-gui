package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI session state persisted between runs: the geometry (location + size) of the
 * main window and every child window (board, debug, per-agent monitors and
 * control dialogs).
 *
 * <p>Windows are keyed by a stable id — {@code "main"}, {@code "board"},
 * {@code "debug"}, {@code "monitor:<agentId>"} and {@code "controls:<agentId>"} —
 * so they can be restored to where the user last left them.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SessionConfig {

    /** Well-known window ids. */
    public static final String MAIN = "main";
    public static final String BOARD = "board";
    public static final String DEBUG = "debug";

    public static String monitor(String agentId) {
        return "monitor:" + agentId;
    }

    public static String controls(String agentId) {
        return "controls:" + agentId;
    }

    private Map<String, WindowState> windows = new LinkedHashMap<>();

    /**
     * Window ids that were open when the app last exited. Geometry lives in
     * {@link #windows} (and is remembered even after a window closes, so it can
     * be reused); this list records which of them should be re-opened on launch.
     */
    private List<String> openWindows = new ArrayList<>();

    public Map<String, WindowState> getWindows() {
        return windows;
    }

    public void setWindows(Map<String, WindowState> windows) {
        this.windows = (windows == null) ? new LinkedHashMap<>() : windows;
    }

    public List<String> getOpenWindows() {
        return openWindows;
    }

    public void setOpenWindows(List<String> openWindows) {
        this.openWindows = (openWindows == null) ? new ArrayList<>() : openWindows;
    }

    public boolean wasOpen(String id) {
        return openWindows.contains(id);
    }

    public WindowState get(String id) {
        return windows.get(id);
    }

    public void put(String id, WindowState state) {
        if (id != null && state != null) {
            windows.put(id, state);
        }
    }

    /** Geometry and frame flags for a single window. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class WindowState {
        private int x;
        private int y;
        private int width;
        private int height;
        /** Window/internal-frame maximized. */
        private boolean maximized;
        /** Internal frame iconified (collapsed to the desktop icon row). */
        private boolean icon;

        public WindowState() {
        }

        public WindowState(int x, int y, int width, int height, boolean maximized, boolean icon) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.maximized = maximized;
            this.icon = icon;
        }

        public static WindowState of(Rectangle bounds, boolean maximized, boolean icon) {
            return new WindowState(bounds.x, bounds.y, bounds.width, bounds.height, maximized, icon);
        }

        public Rectangle bounds() {
            return new Rectangle(x, y, width, height);
        }

        public boolean hasSize() {
            return width > 0 && height > 0;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public boolean isMaximized() {
            return maximized;
        }

        public void setMaximized(boolean maximized) {
            this.maximized = maximized;
        }

        public boolean isIcon() {
            return icon;
        }

        public void setIcon(boolean icon) {
            this.icon = icon;
        }
    }
}
