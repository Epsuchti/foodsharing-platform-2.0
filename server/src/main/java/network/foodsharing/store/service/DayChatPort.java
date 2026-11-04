package network.foodsharing.store.service;

import java.util.Collection;
import java.util.UUID;

/**
 * SPI (pattern A22, like {@link TeamChatPort}): the pickup module creates and fills the day chats of a store (PU3,
 * decision 164) only through this port; the messaging module implements it (nobody depends on messaging).
 */
public interface DayChatPort {

    /** A new named GROUP conversation with the actor and the given members; returns its id. */
    UUID createGroup(UUID actorId, Collection<UUID> memberIds, String name);

    /** Adds those of {@code userIds} who are not members yet (nobody is removed); returns how many were added. */
    int addMissingMembers(UUID conversationId, Collection<UUID> userIds);

    /** The conversation's display name. */
    String name(UUID conversationId);
}
