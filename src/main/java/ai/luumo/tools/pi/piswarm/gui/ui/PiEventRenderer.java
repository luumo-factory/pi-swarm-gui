package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.AgentEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link AgentEvent}s from an agent's out stream into a {@link PiOutputPane}
 * in a pi-terminal-like style.
 */
public final class PiEventRenderer {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ThemeManager theme;
    private final PiOutputPane out;

    public PiEventRenderer(ThemeManager theme, PiOutputPane out) {
        this.theme = theme;
        this.out = out;
    }

    public void render(AgentEvent event) {
        switch (event.type()) {
            case "agent_start" -> divider("agent thinking…", theme.accent());
            case "turn_end" -> renderTurnEnd(event);
            case "agent_end" -> renderAgentEnd(event);
            case "model_select", "set_model_result" -> renderModel(event);
            case "session_reset" -> note("● context reset", theme.warning());
            case "reloading" -> note("● reloading extensions", theme.warning());
            case "pong" -> note("● pong", theme.muted());
            case "ack" -> note("● ack: " + textOf(event.raw(), "action", ""), theme.muted());
            case "error" -> note("✖ " + textOf(event.raw(), "error", "error"), theme.error());
            default -> note("● " + event.type(), theme.muted());
        }
    }

    private void renderTurnEnd(AgentEvent event) {
        List<String> tools = stringArray(event.raw().get("tools"));
        if (tools.isEmpty()) {
            return;
        }
        timestamp(event.ts());
        out.append("⚒ ", theme.tool(), true);
        out.append(String.join(", ", tools), theme.tool());
        out.newline();
    }

    private void renderAgentEnd(AgentEvent event) {
        String text = event.text();
        timestamp(event.ts());
        out.append("● ", theme.success(), true);
        out.append("assistant", theme.muted(), false, true);
        out.newline();
        if (text != null && !text.isBlank()) {
            out.append(text.stripTrailing(), theme.assistant());
            out.newline();
        }
        out.newline();
    }

    private void renderModel(AgentEvent event) {
        JsonNode model = event.raw().get("model");
        String label = model == null || model.isNull()
                ? "(none)"
                : firstNonBlank(textOf(model, "name", null), textOf(model, "id", null), "model");
        boolean ok = !event.raw().has("ok") || event.raw().get("ok").asBoolean();
        note("◆ model → " + label + (ok ? "" : " (failed)"), ok ? theme.accent() : theme.error());
    }

    /** Render a user/board/system message the GUI just sent, for local echo. */
    public void renderOutgoing(String prefix, String text) {
        out.append("❯ ", theme.userMessage(), true);
        out.append(prefix.isBlank() ? "" : prefix + " ", theme.muted());
        out.append(text, theme.userMessage());
        out.newline();
        out.newline();
    }

    public void renderInfo(String text) {
        note(text, theme.muted());
    }

    private void divider(String label, java.awt.Color color) {
        out.append("\n┄┄ ", color);
        out.append(label, color, false, true);
        out.append(" ┄┄\n", color);
    }

    private void note(String text, java.awt.Color color) {
        out.append(text + "\n", color);
    }

    private void timestamp(long ts) {
        out.append(TIME.format(Instant.ofEpochMilli(ts)) + "  ", theme.muted());
    }

    // ------------------------------------------------------------------

    private static List<String> stringArray(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    out.add(item.asText());
                }
            }
        }
        return out;
    }

    private static String textOf(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? fallback : v.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
