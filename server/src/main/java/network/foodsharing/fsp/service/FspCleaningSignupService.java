package network.foodsharing.fsp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.CleaningEligibilityReason;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspAction;
import network.foodsharing.fsp.domain.FspCleaningShift;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningShiftRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cleaning sign-up, sign-out, removal and confirmation (FS2). Sign-ups are serialised per Fairteiler by a row lock on
 * the Fairteiler; the first failing reason answers 409 CLEANING_NOT_ELIGIBLE (args.reason). Bells: an unconfirmed
 * sign-up to the managers, confirmed or removed to the person. Every action is an FSP history entry with
 * {@code metadata.shiftAt}.
 */
@Service
public class FspCleaningSignupService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final FspCleaningScheduleService schedule;
    private final FoodSharePointRepository fsps;
    private final FspCleaningShiftRepository shifts;
    private final FspCleaningSignupRepository signups;
    private final FspFollowerRepository followers;
    private final FspPermissionService access;
    private final CurrentActorService actors;
    private final UserService users;
    private final NotificationService notifications;
    private final HistoryService history;
    private final Clock clock;

    public FspCleaningSignupService(FspCleaningScheduleService schedule, FoodSharePointRepository fsps,
                                    FspCleaningShiftRepository shifts,
                                    FspCleaningSignupRepository signups, FspFollowerRepository followers,
                                    FspPermissionService access, CurrentActorService actors, UserService users,
                                    NotificationService notifications, HistoryService history, Clock clock) {
        this.schedule = schedule;
        this.fsps = fsps;
        this.shifts = shifts;
        this.signups = signups;
        this.followers = followers;
        this.access = access;
        this.actors = actors;
        this.users = users;
        this.notifications = notifications;
        this.history = history;
        this.clock = clock;
    }

    static String unconfirmedBell(UUID fspId) {
        return NotificationType.FSP_CLEANING_SIGNUP_UNCONFIRMED.name() + "-" + fspId;
    }

    /**
     * Signs the current user up; managers are pre-confirmed, otherwise the managers get a grouped bell. A regular
     * occurrence becomes a {@code fsp_cleaning_shift} row.
     *
     * @return whether the sign-up is confirmed
     * @throws ApiException 404, 409 CLEANING_NOT_ENABLED / CLEANING_NOT_ELIGIBLE (args.reason)
     */
    @Transactional
    public boolean signUp(UUID fspId, Instant shiftAt) {
        schedule.requireCleaning(fspId);
        FoodSharePoint fsp = schedule.lock(fspId);
        SessionPrincipal actor = actors.requirePrincipal();
        FspRules.Facts facts = access.requireVisible(actor, fsp);
        User user = users.require(actor.userId());
        SlotSchedule.Slot<FspCleaningSignup> slot = schedule.find(fsp, shiftAt).orElse(null);
        Instant now = clock.instant();
        ZoneId zone = schedule.zoneOf(fsp);
        List<CleaningEligibilityReason> reasons = FspCleaningRules.eligibility(schedule.person(user, shiftAt, zone),
                shiftAt, now, slot);
        if (!reasons.isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.CLEANING_NOT_ELIGIBLE).withArg("reason",
                    contractReason(reasons.getFirst()));
        }
        if (shifts.findByFspIdAndShiftAt(fspId, shiftAt).isEmpty()) {
            FspCleaningShift row = new FspCleaningShift();
            row.setFspId(fspId);
            row.setShiftAt(shiftAt);
            row.setSlotCount(slot.slotCount());
            row.setDescription(slot.description());
            shifts.save(row);
        }
        boolean confirmed = FspCleaningRules.confirmedAtSignUp(facts, fsp.isCleaningAutoConfirm());
        FspCleaningSignup signup = new FspCleaningSignup();
        signup.setFspId(fspId);
        signup.setUserId(actor.userId());
        signup.setShiftAt(shiftAt);
        signup.setConfirmed(confirmed);
        if (confirmed) {
            // automatic confirmation has no confirming person
            signup.setConfirmedBy(FspCleaningRules.preConfirmed(facts) ? actor.userId() : null);
            signup.setConfirmedAt(now);
        }
        signups.save(signup);
        history.record(event(FspAction.CLEANING_SIGNED_UP, fsp, actor.userId(), shiftAt)
                .metadata("confirmed", confirmed)
                .metadata("autoConfirmed", confirmed && !FspCleaningRules.preConfirmed(facts)).build());
        if (!confirmed) {
            List<UUID> managers = followers.findByFspIdAndRole(fspId, FspFollowerRole.MANAGER).stream()
                    .map(FspFollower::getUserId).filter(id -> !id.equals(actor.userId())).toList();
            if (!managers.isEmpty()) {
                notifications.notify(request(managers, NotificationType.FSP_CLEANING_SIGNUP_UNCONFIRMED, fsp, shiftAt,
                        zone).param("userName", user.getDisplayName()).identifier(unconfirmedBell(fspId)).build());
            }
        }
        return confirmed;
    }

    /**
     * Self sign-out (CLEANING_SIGNED_OUT) or removal by a manager (CLEANING_REMOVED, bell to the person).
     *
     * @throws ApiException 404 / SIGNUP_NOT_FOUND, 403, 400 PICKUP_IN_PAST
     */
    @Transactional
    public void signOut(UUID fspId, Instant shiftAt, UUID userId) {
        FoodSharePoint fsp = schedule.requireCleaning(fspId);
        SessionPrincipal actor = actors.requirePrincipal();
        boolean self = actor.userId().equals(userId);
        FspPermissionService.require(FspCleaningRules.maySignOut(access.requireVisible(actor, fsp), self));
        if (shiftAt.isBefore(clock.instant())) {
            throw ApiException.badRequest(ApiErrorCode.PICKUP_IN_PAST);
        }
        FspCleaningSignup signup = signups.findByFspIdAndUserIdAndShiftAt(fspId, userId, shiftAt)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND));
        signups.delete(signup);
        history.record(event(self ? FspAction.CLEANING_SIGNED_OUT : FspAction.CLEANING_REMOVED, fsp, userId, shiftAt)
                .build());
        if (!self) {
            notifications.notify(request(List.of(userId), NotificationType.FSP_CLEANING_REMOVED, fsp, shiftAt,
                    schedule.zoneOf(fsp)).build());
        }
        removeUnconfirmedBellWhenDone(fspId);
    }

    /** @throws ApiException 404 / SIGNUP_NOT_FOUND, 403 unless manager, 409 ALREADY_CONFIRMED */
    @Transactional
    public void confirm(UUID fspId, Instant shiftAt, UUID userId) {
        FoodSharePoint fsp = schedule.requireCleaning(fspId);
        SessionPrincipal actor = actors.requirePrincipal();
        FspPermissionService.require(FspCleaningRules.mayManage(access.requireVisible(actor, fsp)));
        FspCleaningSignup signup = signups.findByFspIdAndUserIdAndShiftAt(fspId, userId, shiftAt)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND));
        if (signup.isConfirmed()) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_CONFIRMED);
        }
        signup.setConfirmed(true);
        signup.setConfirmedBy(actor.userId());
        signup.setConfirmedAt(clock.instant());
        history.record(event(FspAction.CLEANING_CONFIRMED, fsp, userId, shiftAt).build());
        if (!actor.userId().equals(userId)) {
            notifications.notify(request(List.of(userId), NotificationType.FSP_CLEANING_CONFIRMED, fsp, shiftAt,
                    schedule.zoneOf(fsp)).build());
        }
        removeUnconfirmedBellWhenDone(fspId);
    }

    /**
     * Decision 155: a deleted account gives up its places in future shifts; past sign-ups stay with the anonymised
     * user, so team numbers and the statistics stay right.
     *
     * @return number of sign-ups removed
     */
    @Transactional
    public int removeFutureSignups(UUID userId) {
        List<FspCleaningSignup> rows = signups.findByUserIdAndShiftAtGreaterThanEqual(userId, clock.instant());
        for (FspCleaningSignup row : rows) {
            signups.delete(row);
            fsps.findById(row.getFspId()).ifPresent(fsp -> history.record(event(FspAction.CLEANING_REMOVED, fsp,
                    userId, row.getShiftAt()).reason("account deleted").build()));
        }
        rows.stream().map(FspCleaningSignup::getFspId).distinct().forEach(this::removeUnconfirmedBellWhenDone);
        return rows.size();
    }

    /** The contract's reason names (PickupEligibilityReason) for the cleaning reasons. */
    public static String contractReason(CleaningEligibilityReason reason) {
        return reason == CleaningEligibilityReason.SHIFT_IN_PAST ? "PICKUP_IN_PAST" : reason.name();
    }

    private void removeUnconfirmedBellWhenDone(UUID fspId) {
        signups.flush();
        if (!signups.existsByFspIdAndConfirmedFalseAndShiftAtAfter(fspId, clock.instant())) {
            notifications.removeBells(unconfirmedBell(fspId));
        }
    }

    private NotificationRequest.Builder request(List<UUID> recipients, NotificationType type, FoodSharePoint fsp,
                                                Instant shiftAt, ZoneId zone) {
        return NotificationRequest.to(recipients, type).param("fspId", fsp.getId().toString())
                .param("name", fsp.getName()).param("shiftAt", shiftAt.toString())
                .param("when", WHEN.format(shiftAt.atZone(zone))).link(FspCleaningScheduleService.link(fsp.getId()))
                .icon("calendar");
    }

    private HistoryEvent.Builder event(FspAction action, FoodSharePoint fsp, UUID userId, Instant shiftAt) {
        return history.event(action, fsp.getId()).secondarySubject(userId).region(fsp.getRegionId())
                .metadata("shiftAt", shiftAt.toString());
    }
}
