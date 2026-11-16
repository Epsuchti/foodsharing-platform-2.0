package network.foodsharing.fsp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningShiftRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.fsp.repository.FspWallPostRepository;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * FS2 (decision 155) service paths without a database (formerly FspCleaningFlowIT): the reminder marks what it
 * reminded (so the next run skips it) and leaves people with reminders off unmarked, account deletion removes only
 * future sign-ups and the managers' bell once nothing waits for confirmation, and an unknown time zone is refused.
 */
class FspCleaningFlowTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    FspCleaningSignupRepository signups = mock(FspCleaningSignupRepository.class);
    FoodSharePointRepository fsps = mock(FoodSharePointRepository.class);
    UserService users = mock(UserService.class);
    NotificationService notifications = mock(NotificationService.class);
    HistoryService history = mock(HistoryService.class);

    static FoodSharePoint fsp() {
        FoodSharePoint fsp = new FoodSharePoint();
        fsp.setId(UUID.randomUUID());
        fsp.setName("Fairteiler Wiedikon");
        fsp.setStatus(FspStatus.ACTIVE);
        fsp.setCleaningEnabled(true);
        fsp.setTimeZone("Europe/Zurich");
        return fsp;
    }

    static FspCleaningSignup signup(UUID fspId, UUID userId, Instant at) {
        FspCleaningSignup signup = new FspCleaningSignup();
        signup.setFspId(fspId);
        signup.setUserId(userId);
        signup.setShiftAt(at);
        return signup;
    }

    static User user(boolean reminders) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPickupRemindersEnabled(reminders);
        return user;
    }

    @Test
    void reminderMarksTheRemindedSignUpsOnlyAndRingsThem() {
        FoodSharePoint fsp = fsp();
        User wantsIt = user(true);
        User optedOut = user(false);
        Instant shift = NOW.plus(Duration.ofHours(24));
        FspCleaningSignup reminded = signup(fsp.getId(), wantsIt.getId(), shift);
        FspCleaningSignup skipped = signup(fsp.getId(), optedOut.getId(), shift);
        when(signups.findToRemind(NOW.plus(Duration.ofHours(23)), shift)).thenReturn(List.of(reminded, skipped));
        when(fsps.findByIdInAndDeletedAtIsNullOrderByNameAscIdAsc(any())).thenReturn(List.of(fsp));
        when(users.findAll(any())).thenReturn(List.of(wantsIt, optedOut));
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
        FspCleaningReminderJob job = new FspCleaningReminderJob(mock(JobRunner.class), signups, fsps, users, history,
                notifications, new AppProperties(), CLOCK);

        assertThat(job.remind()).isEqualTo(1);

        assertThat(reminded.getRemindedAt()).isEqualTo(NOW);
        assertThat(skipped.getRemindedAt()).isNull();
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.FSP_CLEANING_REMINDER);
        assertThat(bell.getValue().recipientIds()).containsExactly(wantsIt.getId());
    }

    @Test
    void accountDeletionRemovesFutureSignUpsOnlyAndTheManagersBellWhenNothingWaits() {
        FoodSharePoint fsp = fsp();
        UUID user = UUID.randomUUID();
        FspCleaningSignup future = signup(fsp.getId(), user, NOW.plus(Duration.ofDays(30)));
        when(signups.findByUserIdAndShiftAtGreaterThanEqual(user, NOW)).thenReturn(List.of(future));
        when(signups.existsByFspIdAndConfirmedFalseAndShiftAtAfter(fsp.getId(), NOW)).thenReturn(false);
        when(fsps.findById(fsp.getId())).thenReturn(Optional.of(fsp));
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
        FspCleaningSignupService service = new FspCleaningSignupService(mock(FspCleaningScheduleService.class), fsps,
                mock(FspCleaningShiftRepository.class), signups, mock(FspFollowerRepository.class),
                mock(FspPermissionService.class), mock(CurrentActorService.class), users, notifications, history,
                CLOCK);

        assertThat(service.removeFutureSignups(user)).isEqualTo(1);

        verify(signups).delete(future);
        verify(signups, never()).findByUserIdAndShiftAtGreaterThanEqual(user, NOW.minusSeconds(1));
        verify(notifications).removeBells(FspCleaningSignupService.unconfirmedBell(fsp.getId()));
    }

    @Test
    void aManagerRemovalRingsThePersonASelfSignOutDoesNot() {
        FoodSharePoint fsp = fsp();
        UUID person = UUID.randomUUID();
        Instant shift = NOW.plus(Duration.ofDays(5));
        FspCleaningScheduleService schedule = mock(FspCleaningScheduleService.class);
        when(schedule.requireCleaning(fsp.getId())).thenReturn(fsp);
        when(schedule.zoneOf(fsp)).thenReturn(java.time.ZoneId.of("Europe/Zurich"));
        CurrentActorService actors = mock(CurrentActorService.class);
        FspPermissionService access = mock(FspPermissionService.class);
        when(access.requireVisible(any(), any())).thenReturn(
                new FspRules.Facts(false, true, true, true, false, FspStatus.ACTIVE));
        when(signups.findByFspIdAndUserIdAndShiftAt(any(), any(), any()))
                .thenAnswer(call -> Optional.of(signup(fsp.getId(), call.getArgument(1), shift)));
        when(signups.existsByFspIdAndConfirmedFalseAndShiftAtAfter(fsp.getId(), NOW)).thenReturn(true);
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
        FspCleaningSignupService service = new FspCleaningSignupService(schedule, fsps,
                mock(FspCleaningShiftRepository.class), signups, mock(FspFollowerRepository.class), access, actors,
                users, notifications, history, CLOCK);

        when(actors.requirePrincipal()).thenReturn(TestAuth.principal(SeedUsers.BIEB));
        service.signOut(fsp.getId(), shift, person);
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.FSP_CLEANING_REMOVED);
        assertThat(bell.getValue().recipientIds()).containsExactly(person);

        when(actors.requirePrincipal()).thenReturn(TestAuth.principal(SeedUsers.FOODSAVER));
        service.signOut(fsp.getId(), shift, SeedUsers.FOODSAVER.id());
        verify(notifications).notify(any());
        verify(notifications, never()).removeBells(any());
        ArgumentCaptor<HistoryEvent> recorded = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(history, org.mockito.Mockito.times(2)).record(recorded.capture());
        assertThat(recorded.getAllValues()).extracting(HistoryEvent::action).containsExactly(
                network.foodsharing.fsp.domain.FspAction.CLEANING_REMOVED,
                network.foodsharing.fsp.domain.FspAction.CLEANING_SIGNED_OUT);
    }

    @Test
    void anUnknownTimeZoneIsRefused() {
        FoodSharePoint fsp = fsp();
        FoodSharePointRepository repo = mock(FoodSharePointRepository.class);
        when(repo.findById(fsp.getId())).thenReturn(Optional.of(fsp));
        CurrentActorService actors = mock(CurrentActorService.class);
        when(actors.requirePrincipal()).thenReturn(TestAuth.principal(SeedUsers.BIEB));
        FspPermissionService access = mock(FspPermissionService.class);
        when(access.requireVisible(any(), any())).thenReturn(
                new FspRules.Facts(false, true, true, true, false, FspStatus.ACTIVE));
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
        FspService service = new FspService(repo, mock(FspFollowerRepository.class), mock(FspWallPostRepository.class),
                access, actors, mock(RegionService.class), mock(GeocodingClient.class), mock(UploadService.class),
                notifications, mock(MailOutboxService.class), users, history, mock(AppProperties.class), CLOCK,
                mock(FeatureToggleService.class), mock(FspCleaningRegionService.class));

        assertThatThrownBy(() -> service.update(fsp.getId(), new FspService.FspInput(null, null, null, null, null,
                null, null, null, null, null, null, "Mars/Base"))).isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
                    assertThat(((ApiException) e).getArgs()).containsEntry("field", "timeZone");
                });
        assertThat(fsp.getTimeZone()).isEqualTo("Europe/Zurich");

        service.update(fsp.getId(), new FspService.FspInput(null, null, null, null, null, null, null, null, null,
                null, null, "Europe/Paris"));
        assertThat(fsp.getTimeZone()).isEqualTo("Europe/Paris");
    }
}
