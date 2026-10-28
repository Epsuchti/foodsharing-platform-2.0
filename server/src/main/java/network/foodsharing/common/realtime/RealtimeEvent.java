package network.foodsharing.common.realtime;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope of every realtime event sent to {@code /user/queue/events} (A32). The payload is type specific and
 * serialised as JSON; it never contains more than the recipient may read.
 */
public record RealtimeEvent(RealtimeEventType type, Instant at, Map<String, Object> payload) {

    public RealtimeEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
