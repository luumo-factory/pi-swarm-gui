package ai.luumo.tools.pi.piswarm.gui.model;

/**
 * A single post on a swarm message board ({@code NS/board} for the default
 * {@code red} group, or {@code NS/board/<group>} for the others).
 *
 * @param seq      monotonically-increasing per-publisher sequence number
 * @param fromId   slug id of the publisher
 * @param fromName human-friendly name of the publisher
 * @param text     message body
 * @param urgent   whether peers should react immediately
 * @param ts       epoch millis
 * @param group    id of the group board this post belongs to (e.g. {@code red});
 *                 derived from the topic, never present in the wire payload
 */
public record BoardPost(long seq, String fromId, String fromName, String text, boolean urgent, long ts,
                        String group) {

    /** The group this post belongs to, resolved to an {@link AgentGroup}. */
    public AgentGroup groupOrDefault() {
        return AgentGroup.fromId(group);
    }
}
