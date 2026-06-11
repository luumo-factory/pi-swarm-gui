package ai.luumo.tools.pi.piswarm.gui.model;

import java.nio.charset.StandardCharsets;

/**
 * A raw MQTT message as received off the wire, retained for the debug view.
 *
 * <p>The payload is kept verbatim (decoded as UTF-8) so the debug window can
 * show the exact bytes the broker delivered, optionally pretty-printing JSON.</p>
 *
 * @param topic    the full MQTT topic the message arrived on
 * @param payload  the message body decoded as UTF-8 (may be empty)
 * @param qos      the delivered QoS level
 * @param retained whether the broker flagged the message as retained
 * @param ts       receipt time in epoch millis
 */
public record RawMessage(String topic, String payload, int qos, boolean retained, long ts) {

    public static RawMessage of(String topic, byte[] body, int qos, boolean retained) {
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        return new RawMessage(topic, text, qos, retained, System.currentTimeMillis());
    }
}
