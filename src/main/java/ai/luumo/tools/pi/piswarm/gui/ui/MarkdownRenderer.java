package ai.luumo.tools.pi.piswarm.gui.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a Markdown string into a {@link PiOutputPane} as a sequence of styled
 * segments, in the spirit of the pi TUI markdown view: the text is processed by
 * a series of transforms — fenced code blocks, then block-level constructs
 * (headings, lists, block quotes, rules), then inline emphasis/code/links — each
 * emitting styled spans while preserving the monospaced terminal aesthetic.
 */
public final class MarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern HR = Pattern.compile("^(?:-{3,}|\\*{3,}|_{3,})$");
    private static final Pattern UNORDERED = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.*)$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>\\s?(.*)$");

    // Inline emphasis tokens, longest first so e.g. "**" beats "*".
    private static final String[] EMPHASIS = {"***", "___", "~~", "**", "__", "*", "_"};

    private final ThemeManager theme;
    private final PiOutputPane out;

    public MarkdownRenderer(ThemeManager theme, PiOutputPane out) {
        this.theme = theme;
        this.out = out;
    }

    /** Render markdown using the default assistant foreground colour. */
    public void render(String markdown) {
        render(markdown, theme.assistant());
    }

    public void render(String markdown, Color base) {
        if (markdown == null) {
            return;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean inFence = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (isFence(trimmed)) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                // Render code-block lines verbatim with a highlighted background.
                out.appendStyled(line.isEmpty() ? " " : line,
                        theme.codeForeground(), theme.codeBackground(), false, false, false, false);
                out.newline();
                continue;
            }
            renderBlockLine(line, base);
        }
    }

    private static boolean isFence(String trimmed) {
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    // ------------------------------------------------------------------
    // Block-level transforms
    // ------------------------------------------------------------------

    private void renderBlockLine(String line, Color base) {
        if (line.isBlank()) {
            out.newline();
            return;
        }

        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            int level = heading.group(1).length();
            String text = heading.group(2).strip();
            out.appendStyled(text, theme.heading(), null, true, false, level <= 2, false);
            out.newline();
            return;
        }

        if (HR.matcher(line.strip()).matches()) {
            out.append("────────────────────────────────────────\n", theme.muted());
            return;
        }

        Matcher quote = BLOCKQUOTE.matcher(line);
        if (quote.matches()) {
            out.append("│ ", theme.quote());
            renderInline(quote.group(1), theme.quote(), true);
            return;
        }

        Matcher unordered = UNORDERED.matcher(line);
        if (unordered.matches()) {
            out.append(unordered.group(1) + "• ", theme.accent());
            renderInline(unordered.group(2), base, false);
            return;
        }

        Matcher ordered = ORDERED.matcher(line);
        if (ordered.matches()) {
            out.append(ordered.group(1) + ordered.group(2) + ". ", theme.accent());
            renderInline(ordered.group(3), base, false);
            return;
        }

        renderInline(line, base, false);
    }

    // ------------------------------------------------------------------
    // Inline transforms
    // ------------------------------------------------------------------

    private void renderInline(String text, Color base, boolean italicBase) {
        List<Span> spans = new ArrayList<>();
        Style start = new Style();
        start.italic = italicBase;
        parseInline(text, start, spans);
        for (Span span : spans) {
            Color fg = base;
            Color bg = null;
            boolean underline = span.style.underline;
            if (span.style.code) {
                fg = theme.codeForeground();
                bg = theme.codeBackground();
            } else if (span.style.link) {
                fg = theme.link();
                underline = true;
            }
            out.appendStyled(span.text, fg, bg,
                    span.style.bold, span.style.italic, underline, span.style.strike);
        }
        out.newline();
    }

    private void parseInline(String s, Style base, List<Span> out) {
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);

            // Inline code span: `code` (no formatting inside).
            if (c == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i) {
                    flush(plain, base, out);
                    Style st = base.copy();
                    st.code = true;
                    out.add(new Span(s.substring(i + 1, end), st));
                    i = end + 1;
                    continue;
                }
            }

            // Link: [label](url)
            if (c == '[') {
                int close = s.indexOf(']', i + 1);
                if (close > i && close + 1 < s.length() && s.charAt(close + 1) == '(') {
                    int paren = s.indexOf(')', close + 2);
                    if (paren > close) {
                        flush(plain, base, out);
                        Style st = base.copy();
                        st.link = true;
                        out.add(new Span(s.substring(i + 1, close), st));
                        i = paren + 1;
                        continue;
                    }
                }
            }

            // Emphasis: ***/___/~~/**/__/*/_
            String token = emphasisAt(s, i);
            if (token != null) {
                int close = s.indexOf(token, i + token.length());
                if (close > i) {
                    flush(plain, base, out);
                    Style st = base.copy();
                    applyEmphasis(st, token);
                    parseInline(s.substring(i + token.length(), close), st, out);
                    i = close + token.length();
                    continue;
                }
            }

            plain.append(c);
            i++;
        }
        flush(plain, base, out);
    }

    private static String emphasisAt(String s, int i) {
        for (String token : EMPHASIS) {
            if (s.startsWith(token, i)) {
                return token;
            }
        }
        return null;
    }

    private static void applyEmphasis(Style st, String token) {
        switch (token) {
            case "***", "___" -> {
                st.bold = true;
                st.italic = true;
            }
            case "**", "__" -> st.bold = true;
            case "~~" -> st.strike = true;
            default -> st.italic = true; // * or _
        }
    }

    private static void flush(StringBuilder plain, Style style, List<Span> out) {
        if (plain.length() > 0) {
            out.add(new Span(plain.toString(), style.copy()));
            plain.setLength(0);
        }
    }

    // ------------------------------------------------------------------

    private record Span(String text, Style style) {
    }

    private static final class Style {
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        boolean code;
        boolean link;

        Style copy() {
            Style s = new Style();
            s.bold = bold;
            s.italic = italic;
            s.underline = underline;
            s.strike = strike;
            s.code = code;
            s.link = link;
            return s;
        }
    }
}
