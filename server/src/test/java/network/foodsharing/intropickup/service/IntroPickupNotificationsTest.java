package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSubscription;
import network.foodsharing.intropickup.domain.SubscriptionChannel;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupSubscriptionRepository;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.mail.service.MailTemplateService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.StaticMessageSource;

/** Subscribers are unverified by definition: "new bookable" names no business (decision 153, S17 D5). */
class IntroPickupNotificationsTest {

    private final IntroPickupRepository pickups = mock(IntroPickupRepository.class);
    private final IntroPickupSubscriptionRepository subscriptions = mock(IntroPickupSubscriptionRepository.class);
    private final IntroPickupBookingService booking = mock(IntroPickupBookingService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MailOutboxService mails = mock(MailOutboxService.class);
    private final RegionService regions = mock(RegionService.class);
    private final UserService users = mock(UserService.class);
    private final IntroPickupCities cities = mock(IntroPickupCities.class);
    private final AppProperties properties = new AppProperties();
    private final IntroPickupNotifications service = new IntroPickupNotifications(pickups,
            mock(IntroPickupSlotRepository.class), subscriptions, booking, mock(IntroPickupSubscriptionService.class),
            notifications, mails, regions, users, properties, cities);
    private final UUID region = UUID.randomUUID();
    private final IntroPickup pickup = new IntroPickup();

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("https://foodsharing.example");
        pickup.setId(UUID.randomUUID());
        pickup.setRegionId(region);
        pickup.setCategory(IntroPickupCategory.BAKERY);
        pickup.setBusinessName("Bäckerei Vohdin");
        pickup.setStartAt(Instant.parse("2026-11-20T17:00:00Z"));
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName("Mia");
        when(users.findActive(user.getId())).thenReturn(Optional.of(user));
        when(booking.notBookableReason(user.getId(), pickup)).thenReturn(Optional.empty());
        IntroPickupSubscription subscription = new IntroPickupSubscription();
        subscription.setUserId(user.getId());
        subscription.setChannel(SubscriptionChannel.EMAIL);
        subscription.setDeliveryEmail("mia@local");
        subscription.setUnsubscribeToken("token");
        subscription.setLanguage("de");
        when(subscriptions.findByRegionIdAndActiveTrue(region)).thenReturn(List.of(subscription));
        when(regions.zoneOf(region)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(regions.require(region)).thenReturn(new Region());
        when(cities.of(any(), any())).thenReturn(Map.of(pickup.getId(), "Zürich"));
    }

    @Test
    void mailAndBellCarryNoBusinessNameOrCategory() {
        assertThat(service.notifySubscribers(pickup)).isEqualTo(1);

        ArgumentCaptor<MailDraft> mail = ArgumentCaptor.forClass(MailDraft.class);
        verify(mails).enqueue(mail.capture());
        assertThat(mail.getValue().variables()).doesNotContainKeys("businessName", "category")
                .containsEntry("city", "Zürich").containsEntry("online", false);
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().params()).doesNotContainKeys("businessName", "category")
                .containsEntry("city", "Zürich");
        assertThat(bell.getValue().params().values()).doesNotContain("Bäckerei Vohdin");
    }

    @Test
    void anOnlinePickupHasNoCity() {
        pickup.setCategory(IntroPickupCategory.ONLINE);
        service.notifySubscribers(pickup);

        ArgumentCaptor<MailDraft> mail = ArgumentCaptor.forClass(MailDraft.class);
        verify(mails).enqueue(mail.capture());
        assertThat(mail.getValue().variables()).doesNotContainKey("city").containsEntry("online", true);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void theMailRendersWithACityOrOnline(Language language) {
        StaticMessageSource messages = new StaticMessageSource();
        MailTemplateService templates = new MailTemplateService(messages, properties);
        Map<String, Object> onSite = Map.of("displayName", "Mia", "when", "20.11.2026 18:00", "online", false,
                "city", "Zürich", "link", "https://x", "unsubscribeLink", "https://y");
        assertThat(templates.render("intropickup/new-bookable", language, onSite).textBody()).contains("Zürich")
                .contains("20.11.2026 18:00");
        Map<String, Object> online = Map.of("displayName", "Mia", "when", "20.11.2026 18:00", "online", true,
                "link", "https://x", "unsubscribeLink", "https://y");
        assertThat(templates.render("intropickup/new-bookable", language, online).textBody()).containsAnyOf("online",
                "en ligne");
    }
}
