package network.foodsharing.notification.telegram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.notification.service.StoreAlertAccess;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat.OwnerType;
import network.foodsharing.notification.telegram.repository.TelegramAlertChatRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A28 alert chats: the list of chats the bot has seen is for ORGA, ambassadors and store managers; region chats need
 * region administration, store chats mayEditStore; a test message goes only to a chat configured for the owner.
 */
class TelegramChatServiceTest {

    private final TelegramAlertChatRepository chats = mock(TelegramAlertChatRepository.class);
    private final TelegramSeenChats seen = mock(TelegramSeenChats.class);
    private final TelegramDeliveryService delivery = mock(TelegramDeliveryService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final StoreAlertAccess storeAccess = mock(StoreAlertAccess.class);
    @SuppressWarnings("unchecked")
    private final TelegramChatService service = new TelegramChatService(chats, seen, delivery, permissions, storeAccess,
            mock(HistoryService.class, RETURNS_DEEP_STUBS), mock(ObjectProvider.class));

    private final UUID store = UUID.randomUUID();
    private final UUID region = UUID.randomUUID();

    private static SessionPrincipal principal(Role role) {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "x@test.local");
    }

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    @BeforeEach
    void setUp() {
        TelegramAlertChat configured = new TelegramAlertChat();
        configured.setOwnerType(OwnerType.STORE);
        configured.setOwnerId(store);
        configured.setChatId("-1009");
        configured.setTitle("Fairteiler Gruppe");
        when(chats.findByOwnerTypeAndOwnerIdOrderByTitleAscIdAsc(OwnerType.STORE, store)).thenReturn(List.of(configured));
        when(seen.recent()).thenReturn(List.of());
        when(delivery.text(anyString(), any(), any())).thenReturn("Test");
    }

    @Test
    void seenChatsAreForOrgaAmbassadorsAndStoreManagersOnly() {
        SessionPrincipal orga = principal(Role.ORGA);
        when(permissions.isOrga(orga)).thenReturn(true);
        assertThat(service.seenChats(orga)).isEmpty();
        SessionPrincipal ambassador = principal(Role.AMBASSADOR);
        when(permissions.isAmbassadorOfAny(ambassador)).thenReturn(true);
        assertThat(service.seenChats(ambassador)).isEmpty();
        assertThat(service.seenChats(principal(Role.STORE_MANAGER))).isEmpty();
        assertThat(code(() -> service.seenChats(principal(Role.FOODSAVER)))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void regionChatsNeedRegionAdministrationStoreChatsMayEditStore() {
        SessionPrincipal foodsaver = principal(Role.FOODSAVER);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateRegion(foodsaver, region);
        assertThat(code(() -> service.list(foodsaver, OwnerType.REGION, region))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(code(() -> service.list(foodsaver, OwnerType.STORE, store))).isEqualTo(ApiErrorCode.ACCESS_DENIED);

        SessionPrincipal manager = principal(Role.STORE_MANAGER);
        when(storeAccess.mayEditStore(manager, store)).thenReturn(true);
        assertThat(service.list(manager, OwnerType.STORE, store)).extracting(TelegramChatService.ChatConfig::chatId)
                .containsExactly("-1009");
    }

    @Test
    void aTestMessageGoesOnlyToAConfiguredChat() {
        SessionPrincipal manager = principal(Role.STORE_MANAGER);
        when(storeAccess.mayEditStore(manager, store)).thenReturn(true);

        service.sendTest(manager, OwnerType.STORE, store, "-1009");
        verify(delivery).sendText(eq("-1009"), anyString(), eq(null), eq("TELEGRAM_CHAT_TEST"));

        assertThatThrownBy(() -> service.sendTest(manager, OwnerType.STORE, store, "-1002"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
        verify(delivery, never()).sendText(eq("-1002"), anyString(), any(), anyString());
    }
}
