package network.foodsharing.intropickup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q4 transition (decision 167): released guides (direct CAN_GIVE_INTRO_PICKUPS grants) must pass the EinAb guide
 * quiz by {@code app.intropickup.guide-quiz-deadline}.
 * <ul>
 *   <li>Before the deadline: bell and mail GUIDE_QUIZ_REQUIRED to every guide without a valid pass, once in the
 *       {@code initial} phase (first run after deploy) and once in the {@code final} phase (from 14 days before the
 *       deadline). De-duplicated by the bell identifier {@code GUIDE_QUIZ_REQUIRED-<phase>-<userId>}.</li>
 *   <li>From the deadline on: revokes the grant in every region (history GUIDE_DISABLED, reason "guide quiz not
 *       passed"), bell and mail GUIDE_RELEASE_REVOKED to the guide, and one bell GUIDE_EINABS_NEED_GUIDE per region
 *       to its ambassadors and CAN_MANAGE_USERS holders when the guide has upcoming EinAbs there. The EinAbs and
 *       their bookings stay; admins reassign them (EA12). Idempotent: a revoked guide has no grant left.</li>
 * </ul>
 */
@Service
public class GuideQuizTransitionService {

    static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    static final int FINAL_REMINDER_DAYS = 14;
    static final String REVOKE_REASON = "guide quiz not passed";
    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RegionMembersService members;
    private final RegionPermissionResolver resolver;
    private final RegionReadService regionReads;
    private final QuizSessionService quizzes;
    private final IntroPickupRepository pickups;
    private final NotificationService notifications;
    private final UserService users;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public GuideQuizTransitionService(RegionMembersService members, RegionPermissionResolver resolver,
                                      RegionReadService regionReads, QuizSessionService quizzes,
                                      IntroPickupRepository pickups, NotificationService notifications,
                                      UserService users, HistoryService history, AppProperties properties,
                                      Clock clock) {
        this.members = members;
        this.resolver = resolver;
        this.regionReads = regionReads;
        this.quizzes = quizzes;
        this.pickups = pickups;
        this.notifications = notifications;
        this.users = users;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    public LocalDate deadline() {
        return properties.getIntropickup().getGuideQuizDeadline();
    }

    /** Is the deadline reached (today in Zurich)? */
    public boolean deadlineReached() {
        return !LocalDate.ofInstant(clock.instant(), ZONE).isBefore(deadline());
    }

    /** @return the number of reminders sent */
    @Transactional
    @NoHistory(reason = "reminder bookkeeping only: the bells are de-duplicated by their identifier")
    public int remind() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        LocalDate deadline = deadline();
        if (!today.isBefore(deadline)) {
            return 0;
        }
        String phase = today.isBefore(deadline.minusDays(FINAL_REMINDER_DAYS)) ? "initial" : "final";
        int sent = 0;
        for (UUID userId : withoutPass(members.guideReleases()).keySet()) {
            String identifier = BellService.identifier(NotificationType.GUIDE_QUIZ_REQUIRED, phase, userId);
            if (notifications.sentSince(identifier, Instant.EPOCH)) {
                continue;
            }
            notifications.notify(NotificationRequest.to(List.of(userId), NotificationType.GUIDE_QUIZ_REQUIRED)
                    .param("deadline", DAY.format(deadline)).param("phase", phase)
                    .link("/quizzes/EINAB_GUIDE").mail("intropickup/guide-quiz-required").identifier(identifier)
                    .icon("graduation-cap").build());
            sent++;
        }
        return sent;
    }

    /** @return the number of revoked grants (user × region) */
    @Transactional
    public int revokeWithoutPass() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        if (today.isBefore(deadline())) {
            return 0;
        }
        Instant now = clock.instant();
        int revoked = 0;
        for (Map.Entry<UUID, Set<UUID>> guide : withoutPass(members.guideReleases()).entrySet()) {
            UUID userId = guide.getKey();
            String guideName = users.findActive(userId).map(User::getDisplayName).orElse("");
            int revokedHere = 0;
            for (UUID regionId : guide.getValue()) {
                if (!members.withdrawGuide(regionId, userId)) {
                    continue;
                }
                revokedHere++;
                history.record(history.event(IntroPickupAction.GUIDE_DISABLED, userId).secondarySubject(userId)
                        .region(regionId).reason(REVOKE_REASON).build());
                long upcoming = pickups.findByGuide(userId).stream()
                        .filter((IntroPickup p) -> regionId.equals(p.getRegionId()) && p.getStartAt().isAfter(now))
                        .count();
                if (upcoming > 0) {
                    Set<UUID> admins = new LinkedHashSet<>();
                    regionReads.ambassadorsOf(regionId).forEach(admin -> admins.add(admin.getId()));
                    admins.addAll(resolver.holders(Permission.CAN_MANAGE_USERS, regionId));
                    admins.remove(userId);
                    if (!admins.isEmpty()) {
                        notifications.notify(NotificationRequest.to(admins, NotificationType.GUIDE_EINABS_NEED_GUIDE)
                                .param("guideName", guideName).param("count", String.valueOf(upcoming))
                                .link("/regions/" + regionId + "/intro-pickups/admin")
                                .identifier(BellService.identifier(NotificationType.GUIDE_EINABS_NEED_GUIDE, userId,
                                        regionId))
                                .icon("calendar-x").build());
                    }
                }
            }
            if (revokedHere > 0) {
                revoked += revokedHere;
                notifications.notify(NotificationRequest.to(List.of(userId), NotificationType.GUIDE_RELEASE_REVOKED)
                        .param("deadline", DAY.format(deadline())).link("/quizzes/EINAB_GUIDE")
                        .mail("intropickup/guide-release-revoked").icon("user-minus").build());
            }
        }
        return revoked;
    }

    private Map<UUID, Set<UUID>> withoutPass(Map<UUID, Set<UUID>> releases) {
        Set<UUID> passed = quizzes.validPasses(releases.keySet(), QuizKind.EINAB_GUIDE).keySet();
        Map<UUID, Set<UUID>> result = new java.util.TreeMap<>();
        releases.forEach((userId, regions) -> {
            if (!passed.contains(userId)) {
                result.put(userId, regions);
            }
        });
        return result;
    }
}
