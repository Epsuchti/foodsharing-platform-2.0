package network.foodsharing.notification.telegram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.notification.service.StoreAlertAccess;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat.OwnerType;
import network.foodsharing.notification.telegram.repository.TelegramAlertChatRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** AU11: automated Telegram messages sent on behalf of a region end with the region's signature line. */
class RegionSenderTelegramTest {

    static final UUID STORE = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();

    final TelegramAlertChatRepository chats = mock(TelegramAlertChatRepository.class);
    final TelegramDeliveryService delivery = mock(TelegramDeliveryService.class);
    final StaticListableBeanFactory beans = new StaticListableBeanFactory();

    TelegramChatService service() {
        return new TelegramChatService(chats, mock(TelegramSeenChats.class), delivery, mock(PermissionService.class),
                mock(StoreAlertAccess.class), mock(HistoryService.class), beans.getBeanProvider(RegionSenderNames.class));
    }

    static TelegramAlertChat chat(String id) {
        TelegramAlertChat chat = new TelegramAlertChat();
        chat.setChatId(id);
        return chat;
    }

    @Test
    void signatureHelpers() {
        assertThat(RegionSenderNames.fromName("Stadt Zürich")).isEqualTo("foodsharing Stadt Zürich");
        assertThat(RegionSenderNames.signed("Hallo", "Bern")).isEqualTo("Hallo\n\n— foodsharing Bern");
        assertThat(RegionSenderNames.signed("Hallo", null)).isEqualTo("Hallo");
        assertThat(RegionSenderNames.signed("Hallo", " ")).isEqualTo("Hallo");
        assertThat(RegionSenderNames.signed(null, "Bern")).isEqualTo("— foodsharing Bern");
        assertThat(RegionSenderNames.resolve(beans.getBeanProvider(RegionSenderNames.class), REGION)).isEmpty();
    }

    @Test
    void storeAutomationMessagesAreSignedWithTheStoresRegion() {
        beans.addBean("names", (RegionSenderNames) id -> id.equals(REGION) ? Optional.of("Stadt Zürich")
                : Optional.empty());
        when(chats.findByOwnerTypeAndOwnerIdAndEnabledTrue(OwnerType.STORE, STORE)).thenReturn(List.of(chat("-1")));
        when(chats.findByOwnerTypeAndOwnerIdAndEnabledTrue(OwnerType.REGION, REGION)).thenReturn(List.of(chat("-2")));
        when(delivery.sendText(anyString(), anyString(), isNull(), anyString())).thenReturn(7L);

        service().sendForStore(STORE, REGION, List.of("-1", "-2"), "Freier Platz am Dienstag", "PICKUP_OPEN_SLOT");

        verify(delivery).sendText("-1", "Freier Platz am Dienstag\n\n— foodsharing Stadt Zürich", null,
                "PICKUP_OPEN_SLOT");
        verify(delivery).sendText("-2", "Freier Platz am Dienstag\n\n— foodsharing Stadt Zürich", null,
                "PICKUP_OPEN_SLOT");
    }

    @Test
    void withoutRegionNameTheTextStaysUnsigned() {
        when(chats.findByOwnerTypeAndOwnerIdAndEnabledTrue(OwnerType.STORE, STORE)).thenReturn(List.of(chat("-1")));
        service().sendForStore(STORE, null, List.of(), "Text", "PICKUP_OPEN_SLOT");
        verify(delivery).sendText("-1", "Text", null, "PICKUP_OPEN_SLOT");
    }

    @Test
    void personalTelegramAlertsCarryTheSenderRegionToTheDelivery() {
        beans.addBean("names", (RegionSenderNames) id -> Optional.of("Stadt Zürich"));
        TelegramChannel channel = new TelegramChannel(delivery, beans.getBeanProvider(RegionSenderNames.class));
        User recipient = new User();
        recipient.setId(UUID.randomUUID());
        Bell bell = new Bell();
        bell.setTitleKey("bell.PICKUP_SIGNOUT_NOT_REPLACED");

        channel.deliver(recipient, bell, NotificationRequest.to(List.of(recipient.getId()),
                NotificationType.PICKUP_SIGNOUT_NOT_REPLACED).param("storeName", "Bäckerei").senderRegion(REGION).build());
        channel.deliver(recipient, bell, NotificationRequest.to(List.of(recipient.getId()),
                NotificationType.PICKUP_SIGNOUT_NOT_REPLACED).param("storeName", "Bäckerei").build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(delivery, org.mockito.Mockito.times(2)).send(eq(recipient), eq(NotificationType.PICKUP_SIGNOUT_NOT_REPLACED),
                eq("bell.PICKUP_SIGNOUT_NOT_REPLACED"), params.capture(), any(), any());
        assertThat(params.getAllValues().get(0)).containsEntry("senderRegion", "Stadt Zürich")
                .containsEntry("storeName", "Bäckerei");
        assertThat(params.getAllValues().get(1)).doesNotContainKey("senderRegion");
    }
}
