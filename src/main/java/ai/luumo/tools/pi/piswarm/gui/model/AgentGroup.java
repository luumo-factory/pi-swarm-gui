package ai.luumo.tools.pi.piswarm.gui.model;

import java.awt.Color;
import java.util.List;

/**
 * A colour-coded group of agents that share a common message board (and, in
 * future, potentially other resources).
 *
 * <p>There are exactly eight groups, each with a distinct colour. New agents
 * default to {@link #RED}; an unspecified/unknown group id also resolves to
 * {@code RED} via {@link #fromId(String)}.</p>
 */
public enum AgentGroup {
    RED("red", "Red", new Color(0xE53935)),
    ORANGE("orange", "Orange", new Color(0xFB8C00)),
    YELLOW("yellow", "Yellow", new Color(0xF4B400)),
    GREEN("green", "Green", new Color(0x43A047)),
    CYAN("cyan", "Cyan", new Color(0x00ACC1)),
    BLUE("blue", "Blue", new Color(0x1E88E5)),
    PURPLE("purple", "Purple", new Color(0x8E24AA)),
    PINK("pink", "Pink", new Color(0xD81B60));

    /** The group new agents (and unspecified board listeners) belong to. */
    public static final AgentGroup DEFAULT = RED;

    private final String id;
    private final String label;
    private final Color color;

    AgentGroup(String id, String label, Color color) {
        this.id = id;
        this.label = label;
        this.color = color;
    }

    /** Stable lowercase identifier used on the wire and in topic strings. */
    public String id() {
        return id;
    }

    /** Human-friendly capitalised name. */
    public String label() {
        return label;
    }

    /** The distinct colour that designates this group throughout the UI. */
    public Color color() {
        return color;
    }

    /** All eight groups in canonical order. */
    public static List<AgentGroup> all() {
        return List.of(values());
    }

    /** Resolve a group id (case-insensitive); unknown/blank/null resolves to {@link #DEFAULT}. */
    public static AgentGroup fromId(String id) {
        if (id != null) {
            String trimmed = id.trim();
            for (AgentGroup g : values()) {
                if (g.id.equalsIgnoreCase(trimmed)) {
                    return g;
                }
            }
        }
        return DEFAULT;
    }
}
