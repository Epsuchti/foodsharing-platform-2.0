package network.foodsharing.messaging.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Store and region chat entry points with their access rules (A33). */
@Service
public class ConversationLinkService {

    public record StoreChats(UUID teamConversationId, UUID jumperConversationId) {
    }

    public record RegionChat(UUID conversationId, boolean enabled, boolean mayToggle) {
    }

    private final StoreService stores;
    private final StorePermissionService storeRights;
    private final StoreConversationSync storeSync;
    private final RegionService regions;
    private final RegionSettingsService regionSettings;
    private final RegionConversationSync regionSync;
    private final PermissionService permissions;

    public ConversationLinkService(StoreService stores, StorePermissionService storeRights,
                                   StoreConversationSync storeSync, RegionService regions,
                                   RegionSettingsService regionSettings, RegionConversationSync regionSync,
                                   PermissionService permissions) {
        this.stores = stores;
        this.storeRights = storeRights;
        this.storeSync = storeSync;
        this.regions = regions;
        this.regionSettings = regionSettings;
        this.regionSync = regionSync;
        this.permissions = permissions;
    }

    /** Chat ids only when the actor may chat there (mayChatTeam / mayChatJumper); created on first use. */
    @Transactional
    @NoHistory(reason = "ensure records CONVERSATION_CREATED when a chat is created")
    public StoreChats storeChats(SessionPrincipal actor, UUID storeId) {
        Store store = stores.require(storeId);
        StorePermissionService.Rights rights = storeRights.rights(actor, store);
        if (!rights.mayChatTeam() && !rights.mayChatJumper()) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return new StoreChats(rights.mayChatTeam() ? storeSync.ensure(storeId, StoreChat.TEAM) : null,
                rights.mayChatJumper() ? storeSync.ensure(storeId, StoreChat.JUMPER) : null);
    }

    @Transactional
    @NoHistory(reason = "ensure records CONVERSATION_CREATED when a chat is created")
    public RegionChat regionChat(SessionPrincipal actor, UUID regionId) {
        regions.require(regionId);
        boolean mayToggle = permissions.mayAdministrateRegion(actor, regionId);
        if (!mayToggle && !permissions.isMemberOf(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        boolean enabled = regionSettings.conversationEnabled(regionId);
        Optional<UUID> conversationId = enabled ? regionSync.ensure(regionId) : Optional.empty();
        return new RegionChat(conversationId.orElse(null), enabled, mayToggle);
    }

    /** @throws ApiException 403 unless mayAdministrateRegion (checked by RegionSettingsService) */
    @Transactional
    @NoHistory(reason = "RegionSettingsService.setConversationEnabled records SETTINGS_CHANGED")
    public RegionChat setRegionChatEnabled(SessionPrincipal actor, UUID regionId, boolean enabled) {
        regionSettings.setConversationEnabled(regionId, enabled);
        return regionChat(actor, regionId);
    }
}
