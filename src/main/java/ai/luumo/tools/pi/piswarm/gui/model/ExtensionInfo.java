package ai.luumo.tools.pi.piswarm.gui.model;

import java.util.List;

/**
 * A loaded extension as advertised on the registry topic.
 *
 * @param id       extension path/source key
 * @param source   source descriptor (e.g. "extension")
 * @param scope    optional scope (user/project/...)
 * @param origin   optional origin
 * @param tools    tool names this extension registered
 * @param commands command names this extension registered
 * @param active   whether all of this extension's tools are currently active
 */
public record ExtensionInfo(String id, String source, String scope, String origin,
                            List<String> tools, List<String> commands, boolean active) {

    /** Short display name: the trailing path segment of {@link #id}. */
    public String shortName() {
        if (id == null || id.isBlank()) {
            return source == null ? "extension" : source;
        }
        String[] parts = id.split("[\\\\/]");
        return parts[parts.length - 1];
    }
}
