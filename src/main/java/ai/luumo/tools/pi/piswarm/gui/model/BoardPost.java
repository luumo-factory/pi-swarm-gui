package ai.luumo.tools.pi.piswarm.gui.model;

/**
 * A single post on the shared swarm message board ({@code NS/board}).
 *
 * @param seq      monotonically-increasing per-publisher sequence number
 * @param fromId   slug id of the publisher
 * @param fromName human-friendly name of the publisher
 * @param text     message body
 * @param urgent   whether peers should react immediately
 * @param ts       epoch millis
 */
public record BoardPost(long seq, String fromId, String fromName, String text, boolean urgent, long ts) {
}
