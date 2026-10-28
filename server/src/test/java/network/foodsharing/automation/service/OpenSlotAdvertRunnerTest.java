package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.AdvertStatus;
import network.foodsharing.automation.domain.OpenSlotAdvert;
import network.foodsharing.automation.domain.OpenSlotAdvertAudit;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.OpenSlotAdvertRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.telegram.service.TelegramChatService;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import network.foodsharing.store.service.TeamChatPort;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Open-slot adverts (AU4): trigger window, occupied at trigger, filled metric, late cancellation, limits (M4 B5). */
class OpenSlotAdvertRunnerTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final Instant PICKUP = NOW.plus(Duration.ofHours(10));
    static final UUID STORE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();

    OpenSlotAdvertRepository adverts = mock(OpenSlotAdvertRepository.class);
    OpenSlotAdvertAuditRepository audits = mock(OpenSlotAdvertAuditRepository.class);
    PickupScheduleService schedule = mock(PickupScheduleService.class);
    StoreService stores = mock(StoreService.class);
    NotificationService notifications = mock(NotificationService.class);
    TelegramChatService telegram = mock(TelegramChatService.class);
    AppProperties properties = new AppProperties();
    Store store = new Store();
    OpenSlotAdvert advert;
    List<OpenSlotAdvertAudit> existing = new ArrayList<>();
    List<OpenSlotAdvertAudit> saved = new ArrayList<>();
    OpenSlotAdvertRunner runner;
    TeamChatPort teamChat;
    JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        OpenSlotAdvertService service = new OpenSlotAdvertService(adverts, audits, mock(PickupPolicyService.class),
                schedule, mock(HistoryService.class, RETURNS_DEEP_STUBS), json, Clock.fixed(NOW, ZoneOffset.UTC));
        teamChat = mock(TeamChatPort.class);
        runner = new OpenSlotAdvertRunner(adverts, audits, service, schedule, stores, notifications, telegram,
                mock(HistoryService.class, RETURNS_DEEP_STUBS), properties, Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class), teamChat);
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Bäckerei");
        advert = advert(1, 24);
        when(stores.liveStores(any())).thenReturn(List.of(store));
        when(stores.memberAndJumperIds(STORE)).thenReturn(List.of(USER));
        when(schedule.zoneOf(store)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(adverts.findByStoreIdOrderByAdvertNumber(STORE)).thenAnswer(i -> List.of(advert));
        when(audits.findForPickup(any(), any(), any(),
                any())).thenAnswer(i -> existing);
        when(audits.save(any())).thenAnswer(i -> {
            OpenSlotAdvertAudit audit = i.getArgument(0);
            audit.setId(UUID.randomUUID());
            saved.add(audit);
            return audit;
        });
    }

    OpenSlotAdvert advert(int number, int hours) {
        OpenSlotAdvert a = new OpenSlotAdvert();
        a.setId(UUID.randomUUID());
        a.setStoreId(STORE);
        a.setAdvertNumber(number);
        a.setEnabled(true);
        a.setDryRun(false);
        a.setTriggerHoursBefore(hours);
        a.setChannels("STORE_CHAT");
        a.setStoreMessages(json.writeValueAsString(List.of("Frei am {{datetimeDe}} bei {{storeName}}")));
        a.setLateCancellationMessage("Du hast {{datetimeDe}} freigegeben.");
        a.setLateCancellationMaxHours(72);
        return a;
    }

    void slots(PickupSchedule.Slot... slots) {
        when(schedule.schedule(eq(store), any(), any())).thenReturn(List.of(slots));
    }

    static PickupSchedule.Slot slot(Instant at, int total, UUID... users) {
        List<PickupSignup> signups = new ArrayList<>();
        int i = 0;
        for (UUID user : users) {
            PickupSignup s = new PickupSignup();
            s.setId(UUID.randomUUID());
            s.setUserId(user);
            s.setCreatedAt(NOW.minus(Duration.ofDays(5 - i++)));
            signups.add(s);
        }
        return new PickupSchedule.Slot(STORE, at, total, null, true, signups);
    }

    OpenSlotAdvertAudit existingAudit(AdvertStatus status) {
        OpenSlotAdvertAudit audit = new OpenSlotAdvertAudit();
        audit.setId(UUID.randomUUID());
        audit.setStatus(status);
        audit.setUserId(USER);
        existing.add(audit);
        return audit;
    }

    @Test
    void pickupOutsideTheTriggerWindowGetsNoAdvert() {
        advert.setTriggerHoursBefore(5);
        slots(slot(PICKUP, 2));
        var result = runner.run(List.of(advert), false);
        assertThat(result.evaluated()).isZero();
        verify(notifications, never()).notify(any());
    }

    @Test
    void freeSlotInsideTheWindowSendsOneAdvertAndAuditsIt() {
        slots(slot(PICKUP, 2, USER));
        var result = runner.run(List.of(advert), false);
        assertThat(result.confirmed()).isEqualTo(1);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(teamChat).postToTeamChat(org.mockito.ArgumentMatchers.eq(STORE),
                org.mockito.ArgumentMatchers.eq(TeamChatPort.StoreChat.TEAM), text.capture());
        assertThat(text.getValue()).contains("Bäckerei").doesNotContain("{{");
        verify(teamChat).postToTeamChat(STORE, TeamChatPort.StoreChat.JUMPER, text.getValue());
        verify(notifications, never()).notify(any());
        assertThat(saved).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AdvertStatus.SENT);
            assertThat(a.getSentAt()).isEqualTo(NOW);
        });
    }

    @Test
    void existingAdvertIsNotSentAgain() {
        existingAudit(AdvertStatus.SENT);
        slots(slot(PICKUP, 2));
        runner.run(List.of(advert), false);
        verify(notifications, never()).notify(any());
        assertThat(saved).isEmpty();
    }

    @Test
    void occupiedAtTriggerRecordsTheFirstSignup() {
        UUID other = UUID.randomUUID();
        slots(slot(PICKUP, 2, USER, other));
        runner.run(List.of(advert), false);
        assertThat(saved).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AdvertStatus.OCCUPIED_AT_TRIGGER);
            assertThat(a.getUserId()).isEqualTo(USER);
        });
        verify(notifications, never()).notify(any());
    }

    @Test
    void filledAfterAdvertIsMarkedAndClearedWhenFreedAgain() {
        OpenSlotAdvertAudit sent = existingAudit(AdvertStatus.SENT);
        slots(slot(PICKUP, 1, USER));
        runner.run(List.of(advert), false);
        assertThat(sent.getFilledAt()).isEqualTo(NOW);
        slots(slot(PICKUP, 1));
        runner.run(List.of(advert), false);
        assertThat(sent.getFilledAt()).isNull();
    }

    @Test
    void lateCancellationSendsMessageAndAdvert() {
        advert.setSendLateCancellationMessage(true);
        OpenSlotAdvertAudit occupied = existingAudit(AdvertStatus.OCCUPIED_AT_TRIGGER);
        slots(slot(PICKUP, 1));
        runner.run(List.of(advert), false);
        assertThat(occupied.getStatus()).isEqualTo(AdvertStatus.LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT);
        ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(1)).notify(requests.capture());
        NotificationRequest late = requests.getAllValues().getFirst();
        assertThat(late.type()).isEqualTo(NotificationType.PICKUP_LATE_CANCELLATION);
        assertThat(late.recipientIds()).containsExactly(USER);
        assertThat(late.mailTemplate()).isEqualTo("notification/PICKUP_LATE_CANCELLATION");
        verify(teamChat).postToTeamChat(org.mockito.ArgumentMatchers.eq(STORE),
                org.mockito.ArgumentMatchers.eq(TeamChatPort.StoreChat.TEAM), anyString());
    }

    @Test
    void lateCancellationMessageOnlyWhenResendIsOff() {
        advert.setSendLateCancellationMessage(true);
        advert.setResendLatestAdvertAfterLateCancellation(false);
        OpenSlotAdvertAudit occupied = existingAudit(AdvertStatus.OCCUPIED_AT_TRIGGER);
        slots(slot(PICKUP, 1));
        runner.run(List.of(advert), false);
        assertThat(occupied.getStatus()).isEqualTo(AdvertStatus.LATE_CANCELLATION_REMINDER_SENT);
    }

    @Test
    void lateCancellationAdvertOnlyWhenTheMessageIsOff() {
        OpenSlotAdvertAudit occupied = existingAudit(AdvertStatus.OCCUPIED_AT_TRIGGER);
        slots(slot(PICKUP, 1));
        runner.run(List.of(advert), false);
        assertThat(occupied.getStatus()).isEqualTo(AdvertStatus.LATE_CANCELLATION_ADVERTISEMENT_SENT);
    }

    @Test
    void lateCancellationWithoutActionIsRecorded() {
        advert.setResendLatestAdvertAfterLateCancellation(false);
        OpenSlotAdvertAudit occupied = existingAudit(AdvertStatus.OCCUPIED_AT_TRIGGER);
        slots(slot(PICKUP, 1));
        runner.run(List.of(advert), false);
        assertThat(occupied.getStatus()).isEqualTo(AdvertStatus.LATE_CANCELLATION_NO_ACTION);
        verify(notifications, never()).notify(any());
    }

    @Test
    void lateCancellationMessageOnlyWithinTheMaximumHours() {
        advert.setSendLateCancellationMessage(true);
        advert.setLateCancellationMaxHours(2);
        advert.setResendLatestAdvertAfterLateCancellation(false);
        OpenSlotAdvertAudit occupied = existingAudit(AdvertStatus.OCCUPIED_AT_TRIGGER);
        slots(slot(PICKUP, 1));
        runner.run(List.of(advert), false);
        assertThat(occupied.getStatus()).isEqualTo(AdvertStatus.LATE_CANCELLATION_NO_ACTION);
    }

    @Test
    void lateCancellationUsesTheSettingsOfTheLowestAdvertNumber() {
        OpenSlotAdvert first = advert(1, 48);
        first.setEnabled(false);
        first.setSendLateCancellationMessage(true);
        first.setResendLatestAdvertAfterLateCancellation(false);
        OpenSlotAdvert second = advert(2, 24);
        second.setSendLateCancellationMessage(false);
        when(adverts.findByStoreIdOrderByAdvertNumber(STORE)).thenReturn(List.of(first, second));
        OpenSlotAdvertAudit occupied = existingAudit(AdvertStatus.OCCUPIED_AT_TRIGGER);
        slots(slot(PICKUP, 1));
        runner.run(List.of(second), false);
        assertThat(occupied.getStatus()).isEqualTo(AdvertStatus.LATE_CANCELLATION_REMINDER_SENT);
    }

    @Test
    void latestDueAdvertIsTheOneWithTheLatestTriggerTime() {
        OpenSlotAdvert early = advert(1, 24);
        OpenSlotAdvert late = advert(2, 12);
        OpenSlotAdvert notDue = advert(3, 6);
        List<OpenSlotAdvert> all = List.of(early, late, notDue);
        assertThat(OpenSlotAdvertRunner.isLatestDue(late, all, PICKUP, NOW)).isTrue();
        assertThat(OpenSlotAdvertRunner.isLatestDue(early, all, PICKUP, NOW)).isFalse();
        assertThat(OpenSlotAdvertRunner.isLatestDue(notDue, all, PICKUP, NOW)).isFalse();
    }

    @Test
    void maximumAdvertsPerRun() {
        properties.getAutomation().setMaxAdvertisementsPerRun(1);
        slots(slot(PICKUP, 1), slot(PICKUP.plus(Duration.ofHours(1)), 1));
        runner.run(List.of(advert), false);
        verify(teamChat, times(1)).postToTeamChat(org.mockito.ArgumentMatchers.eq(STORE),
                org.mockito.ArgumentMatchers.eq(TeamChatPort.StoreChat.TEAM), anyString());
    }

    @Test
    void telegramAdvertDeletesThePreviousOneOfThePickup() {
        advert.setChannels("TELEGRAM");
        advert.setTelegramChatId("-1002");
        advert.setTelegramMessages(json.writeValueAsString(List.of("Frei: {{freeSlots}}")));
        OpenSlotAdvertAudit previous = new OpenSlotAdvertAudit();
        previous.setId(UUID.randomUUID());
        previous.setTelegramChatId("-1002");
        previous.setTelegramMessageId(7L);
        when(audits.telegramToDelete(STORE, PICKUP, NOW)).thenReturn(List.of(previous));
        when(telegram.sendForStore(eq(STORE), any(), anyCollection(), anyString(), anyString()))
                .thenReturn(List.of(new TelegramChatService.SentMessage("-1002", 8L)));
        slots(slot(PICKUP, 2));
        runner.run(List.of(advert), false);
        verify(telegram).deleteMessage("-1002", 7L);
        assertThat(previous.getTelegramDeletedAt()).isEqualTo(NOW);
        assertThat(saved.getFirst().getTelegramMessageId()).isEqualTo(8L);
    }

    @Test
    void pushChannelSendsPushOnly() {
        advert.setChannels("PUSH");
        advert.setPushMessages(json.writeValueAsString(List.of("Push {{time}}")));
        slots(slot(PICKUP, 2));
        runner.run(List.of(advert), false);
        verify(notifications).pushOnly(any());
        verify(notifications, never()).notify(any());
    }

    @Test
    void dryRunOnlyWritesASummary() {
        advert.setDryRun(true);
        slots(slot(PICKUP, 2));
        var result = runner.run(List.of(advert), false);
        assertThat(result.dryRun()).isFalse();
        verify(notifications, never()).notify(any());
        assertThat(saved).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AdvertStatus.DRY_RUN_SUMMARY);
            assertThat(a.getReason()).contains("would send 1");
        });
    }

    @Test
    void advertWithoutMessagesForAChannelIsSkipped() {
        advert.setChannels("STORE_CHAT,PUSH");
        slots(slot(PICKUP, 2));
        var result = runner.run(List.of(advert), false);
        assertThat(result.skipped()).isEqualTo(1);
        verify(notifications, never()).notify(any());
        verify(notifications, never()).pushOnly(any());
        verify(telegram, never()).sendForStore(any(), any(), anyList(), anyString(), anyString());
    }
}
