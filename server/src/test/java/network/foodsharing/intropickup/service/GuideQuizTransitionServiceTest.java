package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.security.Permission;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Q4 transition (decision 168): reminders before the deadline, revocation from it on, idempotent. */
class GuideQuizTransitionServiceTest {

    private static final UUID ZH = UUID.randomUUID();
    private static final UUID BERN = UUID.randomUUID();
    private static final UUID GUIDE = UUID.randomUUID();
    private static final UUID PASSER = UUID.randomUUID();
    private static final UUID AMBASSADOR = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();
    private static final LocalDate DEADLINE = LocalDate.of(2027, 1, 15);

    private final RegionMembersService members = mock(RegionMembersService.class);
    private final RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    private final RegionReadService regionReads = mock(RegionReadService.class);
    private final QuizSessionService quizzes = mock(QuizSessionService.class);
    private final IntroPickupRepository pickups = mock(IntroPickupRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserService users = mock(UserService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final AppProperties properties = new AppProperties();

    /** Direct grants (user → regions); withdrawGuide removes them like the real service. */
    private final Map<UUID, Set<UUID>> releases = new HashMap<>();
    private final Set<String> sentIdentifiers = new HashSet<>();

    @BeforeEach
    void setUp() {
        properties.getIntropickup().setGuideQuizDeadline(DEADLINE);
        releases.put(GUIDE, new HashSet<>(Set.of(ZH, BERN)));
        releases.put(PASSER, new HashSet<>(Set.of(ZH)));
        when(members.guideReleases()).thenAnswer(invocation -> {
            Map<UUID, Set<UUID>> copy = new HashMap<>();
            releases.forEach((user, regions) -> {
                if (!regions.isEmpty()) {
                    copy.put(user, Set.copyOf(regions));
                }
            });
            return copy;
        });
        when(members.withdrawGuide(any(), any())).thenAnswer(invocation -> {
            Set<UUID> regions = releases.get(invocation.<UUID>getArgument(1));
            return regions != null && regions.remove(invocation.<UUID>getArgument(0));
        });
        when(quizzes.validPasses(any(), eq(QuizKind.EINAB_GUIDE)))
                .thenReturn(Map.of(PASSER, Instant.parse("2026-11-01T10:00:00Z")));
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        when(notifications.sentSince(any(), any())).thenAnswer(invocation ->
                sentIdentifiers.contains(invocation.<String>getArgument(0)));
        when(notifications.notify(any())).thenAnswer(invocation -> {
            NotificationRequest request = invocation.getArgument(0);
            if (request.identifier() != null) {
                sentIdentifiers.add(request.identifier());
            }
            return List.of();
        });
        User guide = new User();
        guide.setId(GUIDE);
        guide.setDisplayName("Julien Favre");
        when(users.findActive(GUIDE)).thenReturn(java.util.Optional.of(guide));
        User ambassador = new User();
        ambassador.setId(AMBASSADOR);
        when(regionReads.ambassadorsOf(ZH)).thenReturn(List.of(ambassador));
        when(resolver.holders(Permission.CAN_MANAGE_USERS, ZH)).thenReturn(Set.of(MANAGER));
    }

    private GuideQuizTransitionService at(String instant) {
        return new GuideQuizTransitionService(members, resolver, regionReads, quizzes, pickups, notifications, users,
                history, properties, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, org.mockito.Mockito.atLeast(0)).notify(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void beforeTheDeadlineOnlyRemindsGuidesWithoutAPassOncePerPhase() {
        GuideQuizTransitionService early = at("2026-11-10T07:15:00Z");
        assertThat(early.deadlineReached()).isFalse();
        assertThat(early.remind()).isEqualTo(1);
        assertThat(early.remind()).isZero();
        assertThat(early.revokeWithoutPass()).isZero();
        verify(members, never()).withdrawGuide(any(), any());
        assertThat(sent()).singleElement().satisfies(request -> {
            assertThat(request.type()).isEqualTo(NotificationType.GUIDE_QUIZ_REQUIRED);
            assertThat(request.recipientIds()).containsExactly(GUIDE);
            assertThat(request.identifier()).isEqualTo("GUIDE_QUIZ_REQUIRED-initial-" + GUIDE);
            assertThat(request.params()).containsEntry("deadline", "15.01.2027");
            assertThat(request.mailTemplate()).isEqualTo("intropickup/guide-quiz-required");
        });
    }

    @Test
    void fourteenDaysBeforeTheDeadlineTheFinalReminderGoesOut() {
        assertThat(at("2026-12-31T22:59:00Z").remind()).isEqualTo(1);
        assertThat(at("2027-01-01T07:15:00Z").remind()).isEqualTo(1);
        assertThat(at("2027-01-10T07:15:00Z").remind()).isZero();
        assertThat(sentIdentifiers).containsExactlyInAnyOrder("GUIDE_QUIZ_REQUIRED-initial-" + GUIDE,
                "GUIDE_QUIZ_REQUIRED-final-" + GUIDE);
    }

    @Test
    void atTheDeadlineGuidesWithoutAPassAreRevokedEverywhereAndAdminsAreToldAboutUpcomingEinAbs() {
        IntroPickup upcoming = new IntroPickup();
        upcoming.setRegionId(ZH);
        upcoming.setGuideId(GUIDE);
        upcoming.setStartAt(Instant.parse("2027-01-20T17:00:00Z"));
        IntroPickup past = new IntroPickup();
        past.setRegionId(BERN);
        past.setGuideId(GUIDE);
        past.setStartAt(Instant.parse("2026-12-01T17:00:00Z"));
        when(pickups.findByGuide(GUIDE)).thenReturn(List.of(upcoming, past));
        List<HistoryEvent> recorded = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            recorded.add(invocation.getArgument(0));
            return null;
        }).when(history).record(any());

        GuideQuizTransitionService due = at("2027-01-14T23:30:00Z"); // 00:30 on the 15th in Zurich
        assertThat(due.deadlineReached()).isTrue();
        assertThat(due.remind()).isZero();
        assertThat(due.revokeWithoutPass()).isEqualTo(2);

        verify(members).withdrawGuide(ZH, GUIDE);
        verify(members).withdrawGuide(BERN, GUIDE);
        verify(members, never()).withdrawGuide(any(), eq(PASSER));
        assertThat(recorded).hasSize(2).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(IntroPickupAction.GUIDE_DISABLED);
            assertThat(event.reason()).isEqualTo("guide quiz not passed");
        });
        assertThat(recorded).extracting(HistoryEvent::regionId).containsExactlyInAnyOrder(ZH, BERN);
        List<NotificationRequest> requests = sent();
        assertThat(requests).filteredOn(r -> r.type() == NotificationType.GUIDE_EINABS_NEED_GUIDE).singleElement()
                .satisfies(request -> {
                    assertThat(request.recipientIds()).containsExactlyInAnyOrder(AMBASSADOR, MANAGER);
                    assertThat(request.params()).containsEntry("count", "1").containsEntry("guideName", "Julien Favre");
                    assertThat(request.link()).isEqualTo("/regions/" + ZH + "/intro-pickups/admin");
                });
        assertThat(requests).filteredOn(r -> r.type() == NotificationType.GUIDE_RELEASE_REVOKED).singleElement()
                .satisfies(request -> assertThat(request.recipientIds()).containsExactly(GUIDE));

        // idempotent: the next run finds nothing to revoke and tells nobody
        assertThat(due.revokeWithoutPass()).isZero();
        verify(notifications, times(2)).notify(argThat(r -> r.type() != NotificationType.GUIDE_QUIZ_REQUIRED));
    }
}
