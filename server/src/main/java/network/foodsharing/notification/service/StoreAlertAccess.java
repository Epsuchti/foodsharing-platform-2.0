package network.foodsharing.notification.service;

import java.util.UUID;
import network.foodsharing.security.SessionPrincipal;

/**
 * Store permission for store alert chats (Telegram), implemented by the store module so that notification does not
 * depend on store (store → notification).
 */
public interface StoreAlertAccess {

    /**
     * mayUseAutomations(store).
     *
     * @throws network.foodsharing.common.error.ApiException 404 when the store does not exist
     */
    boolean mayUseAutomations(SessionPrincipal actor, UUID storeId);

    /** Store name for messages (null when unknown). */
    String storeName(UUID storeId);
}
