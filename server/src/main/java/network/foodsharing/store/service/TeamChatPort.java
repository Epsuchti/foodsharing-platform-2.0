package network.foodsharing.store.service;

import java.util.UUID;

/**
 * SPI (A33, pattern A22): other modules write to store chats and direct conversations only through this port. The
 * default is a no-op ({@link StoreDefaults}); the messaging module provides the {@code @Primary} implementation.
 */
public interface TeamChatPort {

    /** TEAM = members incl. managers, JUMPER = managers + jumpers (decision 52). */
    enum StoreChat {
        TEAM,
        JUMPER
    }

    /** Posts a system message ("foodsharing") to the store's team or jumper chat (created on demand). */
    void postToTeamChat(UUID storeId, StoreChat chat, String text);

    /** Sends a direct message from one user to another (conversation found or created). */
    void sendDirect(UUID fromUserId, UUID toUserId, String text);
}
