package network.foodsharing.messaging.domain;

/** Kind of a conversation (A33). Store and region chats are locked. */
public enum ConversationKind {
    DIRECT,
    GROUP,
    STORE_TEAM,
    STORE_JUMPER,
    REGION
}
