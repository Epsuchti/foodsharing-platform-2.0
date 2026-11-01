package network.foodsharing.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.RegionConversation;
import network.foodsharing.messaging.domain.StoreConversation;
import network.foodsharing.messaging.repository.ConversationRepository;
import network.foodsharing.messaging.repository.RegionConversationRepository;
import network.foodsharing.messaging.repository.StoreConversationRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Resume item 16: who moderates which conversation kind (moderator delete, original text in the history). */
class ChatModerationTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final StoreConversationRepository storeLinks = mock(StoreConversationRepository.class);
    private final RegionConversationRepository regionLinks = mock(RegionConversationRepository.class);
    private final StoreService stores = mock(StoreService.class);
    private final StorePermissionService storeRights = mock(StorePermissionService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ChatModeration moderation = new ChatModeration(conversations, storeLinks, regionLinks, stores,
            storeRights, permissions);

    private final SessionPrincipal orga = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.ORGA, "o@x");
    private final SessionPrincipal manager = principal(Role.STORE_MANAGER);
    private final SessionPrincipal admin = principal(Role.AMBASSADOR);
    private final SessionPrincipal member = principal(Role.FOODSAVER);
    private final UUID storeId = UUID.randomUUID();
    private final UUID regionId = UUID.randomUUID();

    private static SessionPrincipal principal(Role role) {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, role.name() + "@x");
    }

    private static Conversation conversation(ConversationKind kind) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setKind(kind);
        return conversation;
    }

    @BeforeEach
    void setUp() {
        when(permissions.isOrga(orga)).thenReturn(true);
        Store store = new Store();
        store.setId(storeId);
        when(stores.storesById(List.of(storeId))).thenReturn(List.of(store));
        StorePermissionService.Rights edit = mock(StorePermissionService.Rights.class);
        when(edit.mayEditStore()).thenReturn(true);
        StorePermissionService.Rights none = mock(StorePermissionService.Rights.class);
        when(storeRights.rights(any(), eq(store))).thenReturn(none);
        when(storeRights.rights(eq(manager), eq(store))).thenReturn(edit);
        when(permissions.mayAdministrateRegion(admin, regionId)).thenReturn(true);
    }

    private Conversation storeChat(ConversationKind kind) {
        Conversation conversation = conversation(kind);
        StoreConversation link = new StoreConversation();
        link.setStoreId(storeId);
        link.setConversationId(conversation.getId());
        when(storeLinks.findByConversationId(conversation.getId())).thenReturn(Optional.of(link));
        return conversation;
    }

    private Conversation regionChat() {
        Conversation conversation = conversation(ConversationKind.REGION);
        RegionConversation link = new RegionConversation();
        link.setRegionId(regionId);
        link.setConversationId(conversation.getId());
        when(regionLinks.findByConversationId(conversation.getId())).thenReturn(Optional.of(link));
        return conversation;
    }

    @Test
    void orgaModeratesEveryKind() {
        for (ConversationKind kind : ConversationKind.values()) {
            assertThat(moderation.isModerator(orga, conversation(kind))).as(kind.name()).isTrue();
        }
    }

    @Test
    void storeManagersModerateTheirStoreChatsOnly() {
        assertThat(moderation.isModerator(manager, storeChat(ConversationKind.STORE_TEAM))).isTrue();
        assertThat(moderation.isModerator(manager, storeChat(ConversationKind.STORE_JUMPER))).isTrue();
        assertThat(moderation.isModerator(member, storeChat(ConversationKind.STORE_TEAM))).isFalse();
        assertThat(moderation.isModerator(manager, regionChat())).isFalse();
        assertThat(moderation.isModerator(manager, conversation(ConversationKind.DIRECT))).isFalse();
    }

    @Test
    void deletedStoresHaveNoStoreModerators() {
        Store deleted = new Store();
        deleted.setId(storeId);
        deleted.setDeletedAt(Instant.parse("2026-11-01T00:00:00Z"));
        when(stores.storesById(List.of(storeId))).thenReturn(List.of(deleted));
        assertThat(moderation.isModerator(manager, storeChat(ConversationKind.STORE_TEAM))).isFalse();
    }

    @Test
    void regionAdminsModerateTheRegionChatOnly() {
        assertThat(moderation.isModerator(admin, regionChat())).isTrue();
        assertThat(moderation.isModerator(member, regionChat())).isFalse();
        assertThat(moderation.isModerator(admin, storeChat(ConversationKind.STORE_TEAM))).isFalse();
    }

    @Test
    void directAndGroupChatsAreModeratedByOrgaOnly() {
        for (SessionPrincipal actor : List.of(manager, admin, member)) {
            assertThat(moderation.isModerator(actor, conversation(ConversationKind.DIRECT))).isFalse();
            assertThat(moderation.isModerator(actor, conversation(ConversationKind.GROUP))).isFalse();
        }
        assertThat(moderation.isModerator(member, (Conversation) null)).isFalse();
    }
}
