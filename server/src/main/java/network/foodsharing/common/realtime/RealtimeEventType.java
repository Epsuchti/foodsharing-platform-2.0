package network.foodsharing.common.realtime;

/** Realtime envelope types (A32; mirrors {@code RealtimeEventType} in openapi/schemas/messaging.yaml). */
public enum RealtimeEventType {
    CONVERSATION_MESSAGE,
    CONVERSATION_READ,
    CONVERSATION_UPDATED,
    BELL,
    MAILBOX_MAIL,
    FORUM_POST,
    POLL_STATE,
    PING
}
