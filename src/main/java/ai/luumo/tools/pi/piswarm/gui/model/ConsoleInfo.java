package ai.luumo.tools.pi.piswarm.gui.model;

/**
 * A swarm console (spawn host) discovered from its retained
 * {@code NS/console/registry/CID} topic. Consoles fork headless agents on
 * request, so the GUI needs to know which hosts are available to spawn on.
 *
 * @param id        slugified console id (topic component)
 * @param name      human-friendly console name
 * @param host      machine hostname the console runs on
 * @param pid       console process id
 * @param agentCount number of agents this console currently tracks
 * @param online    whether the console is online (false = last-will/offline)
 * @param lastSeen  receipt time, epoch millis
 */
public record ConsoleInfo(String id, String name, String host, int pid,
                          int agentCount, boolean online, long lastSeen) {

    /** Label for pickers, e.g. {@code "host-1  (mybox · 3 agents)"}. */
    public String displayLabel() {
        StringBuilder sb = new StringBuilder(name == null || name.isBlank() ? id : name);
        if (host != null && !host.isBlank()) {
            sb.append("  ·  ").append(host);
        }
        sb.append(agentCount == 1 ? "  ·  1 agent" : "  ·  " + agentCount + " agents");
        return sb.toString();
    }
}
