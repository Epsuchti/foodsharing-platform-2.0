package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.pickup.domain.PickupEligibilityReason;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Sign-up with linked pickups (decision 167 S3), confirmation and sign-out (PU3, PU5), the rules PickupsApiIT and
 * LinkedBookingFlowIT asserted: a pickup that only misses its linked pickup answers LINKED_PICKUP_REQUIRED; the linked
 * pickups of one request are checked and stored one by one with the others counted as booked, a full or refused one
 * answers LINKED_PICKUP_FULL / LINKED_PICKUP_BLOCKED (the transaction of {@code signUp} rolls the whole request back);
 * at most five distinct linked pickups, never the pickup itself. Confirming needs mayConfirmPickups and an unconfirmed
 * sign-up and rings PICKUP_CONFIRMED; a removal by someone else rings PICKUP_REMOVED with the message, a self sign-out
 * rings nobody; past pickups and unknown sign-ups are refused.
 */
class PickupSignupServiceRulesTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final Instant PICKUP = NOW.plus(Duration.ofDays(9));
    static final Instant LINKED_AT = PICKUP.plus(Duration.ofDays(1));
    static final UUID STORE = UUID.randomUUID();
    static final UUID LINKED = UUID.randomUUID();
    static final UUID ME = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    PickupScheduleService schedule = mock(PickupScheduleService.class);
    PickupDateRepository dates = mock(PickupDateRepository.class);
    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StoreService stores = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    List<PickupRuleGate.RuleBlock> refused = new ArrayList<>();
    Store store = store(STORE, "Coop");
    Store linked = store(LINKED, "Migros");
    PickupSignupService service;

    static Store store(UUID id, String name) {
        Store store = new Store();
        store.setId(id);
        store.setRegionId(UUID.randomUUID());
        store.setName(name);
        return store;
    }

    @BeforeEach
    void setUp() {
        service = new PickupSignupService(schedule, dates, signups, mock(PickupExtraSlotRepository.class), stores,
                rights, mock(UserService.class), actor, mock(HistoryService.class, RETURNS_DEEP_STUBS), events,
                new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC), new PickupRuleGate() {
                    @Override
                    public Map<Instant, List<RuleBlock>> blocks(UUID storeId, UUID userId,
                                                                java.util.Collection<Instant> pickupAts) {
                        return Map.of();
                    }

                    @Override
                    public void refused(UUID storeId, UUID userId, Instant pickupAt, List<RuleBlock> blocks) {
                        refused.addAll(blocks);
                    }
                });
        for (Store s : List.of(store, linked)) {
            when(stores.require(s.getId())).thenReturn(s);
            when(stores.lock(s.getId())).thenReturn(s);
            when(schedule.zoneOf(s)).thenReturn(ZoneId.of("Europe/Zurich"));
            when(rights.rights(any(SessionPrincipal.class), eq(s))).thenReturn(rights(true, false));
        }
        when(dates.findByStoreIdAndPickupAt(any(), any())).thenReturn(Optional.empty());
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(ME, UUID.randomUUID(), Role.FOODSAVER,
                "me@test.local"));
        when(rights.teamStatus(any(), any())).thenReturn(UserTeamStatus.MEMBER);
        eligible(store, PICKUP, true);
        when(schedule.ruleCheck(any(), any(), any(), any(), anyCollection())).thenReturn(List.of());
    }

    static StorePermissionService.Rights rights(boolean seePickups, boolean confirm) {
        return new StorePermissionService.Rights(true, true, true, false, false, false, false, false, seePickups,
                false, confirm, false, false, false, false, true, true, false, false, false, false,
                confirm ? UserTeamStatus.COORDINATOR : UserTeamStatus.MEMBER);
    }

    void eligible(Store s, Instant at, boolean free, PickupEligibilityReason... reasons) {
        when(schedule.find(eq(s), eq(at))).thenReturn(free || reasons.length > 0
                ? Optional.of(new PickupSchedule.Slot(s.getId(), at, 3, null, false, List.of())) : Optional.empty());
        PickupScheduleService.EligibilityContext context = mock(PickupScheduleService.EligibilityContext.class);
        when(context.evaluate(eq(at), any())).thenReturn(new PickupEligibilityRules.Result(List.of(reasons), null));
        when(schedule.context(s, ME)).thenReturn(context);
    }

    static PickupRuleGate.RuleBlock block(String rule, UUID... storeIds) {
        return new PickupRuleGate.RuleBlock(rule, new PickupRuleGate.BlockParams(null, null, null, List.of(storeIds),
                null, null, null, null, null, null, null, null), rule);
    }

    static ApiException error(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("no ApiException");
    }

    List<StoreNotifications.Send> bells() {
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(published.capture());
        return published.getAllValues().stream().filter(StoreNotifications.Send.class::isInstance)
                .map(StoreNotifications.Send.class::cast).toList();
    }

    // ------------------------------------------------------------------ linked pickups (decision 167 S3)

    @Test
    void aPickupThatOnlyMissesItsLinkedPickupAnswersLinkedPickupRequired() {
        when(schedule.ruleCheck(eq(store), eq(ME), any(), eq(PICKUP), anyCollection()))
                .thenReturn(List.of(block(PickupScheduleService.LINKED_STORE, LINKED)));
        ApiException ex = error(() -> service.signUp(STORE, PICKUP, List.of()));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LINKED_PICKUP_REQUIRED);
        assertThat(ex.getArgs()).containsEntry("rule", "LINKED_STORE").containsEntry("storeIds", LINKED.toString());
        verify(signups, never()).save(any());
        assertThat(refused).as("a missing link is no refusal to audit").isEmpty();
    }

    @Test
    void bothPickupsAreStoredInOneRequestEachCountingTheOtherAsBooked() {
        eligible(linked, LINKED_AT, true);
        assertThat(service.signUp(STORE, PICKUP, List.of(new PickupRuleGate.Planned(LINKED, LINKED_AT)))).isFalse();
        ArgumentCaptor<PickupSignup> saved = ArgumentCaptor.forClass(PickupSignup.class);
        verify(signups, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PickupSignup::getStoreId, PickupSignup::getPickupAt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(STORE, PICKUP),
                        org.assertj.core.groups.Tuple.tuple(LINKED, LINKED_AT));
        verify(schedule).ruleCheck(eq(store), eq(ME), any(), eq(PICKUP),
                eq(List.of(new PickupRuleGate.Planned(LINKED, LINKED_AT))));
        verify(schedule).ruleCheck(eq(linked), eq(ME), any(), eq(LINKED_AT),
                eq(List.of(new PickupRuleGate.Planned(STORE, PICKUP))));
        // both stores are locked, in id order
        verify(stores).lock(STORE);
        verify(stores).lock(LINKED);
    }

    @Test
    void aFullLinkedPickupAnswersLinkedPickupFull() {
        eligible(linked, LINKED_AT, true, PickupEligibilityReason.NO_FREE_SLOT);
        ApiException ex = error(() -> service.signUp(STORE, PICKUP,
                List.of(new PickupRuleGate.Planned(LINKED, LINKED_AT))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LINKED_PICKUP_FULL);
        assertThat(ex.getArgs()).containsEntry("storeId", LINKED.toString())
                .containsEntry("pickupAt", LINKED_AT.toString());
        assertThat(signUpIsTransactional()).isTrue();
    }

    @Test
    void aLinkedTimeWithoutAPickupOrOutsideTheTeamIsBlockedWithTheReason() {
        eligible(linked, LINKED_AT, false);
        PickupScheduleService.EligibilityContext context = mock(PickupScheduleService.EligibilityContext.class);
        when(context.evaluate(eq(LINKED_AT), any())).thenReturn(new PickupEligibilityRules.Result(
                List.of(PickupEligibilityReason.NO_FREE_SLOT), null));
        when(schedule.context(linked, ME)).thenReturn(context);
        ApiException noPickup = error(() -> service.signUp(STORE, PICKUP,
                List.of(new PickupRuleGate.Planned(LINKED, LINKED_AT))));
        assertThat(noPickup.getCode()).isEqualTo(ApiErrorCode.LINKED_PICKUP_BLOCKED);
        assertThat(noPickup.getArgs()).containsEntry("reason", "NO_FREE_SLOT");

        when(rights.rights(any(SessionPrincipal.class), eq(linked))).thenReturn(rights(false, false));
        ApiException outside = error(() -> service.signUp(STORE, PICKUP,
                List.of(new PickupRuleGate.Planned(LINKED, LINKED_AT))));
        assertThat(outside.getCode()).isEqualTo(ApiErrorCode.LINKED_PICKUP_BLOCKED);
        assertThat(outside.getArgs()).containsEntry("reason", "NOT_TEAM_MEMBER");
    }

    @Test
    void aStoreRuleOfTheLinkedStoreBlocksAndIsAudited() {
        eligible(linked, LINKED_AT, true);
        when(schedule.ruleCheck(eq(linked), eq(ME), any(), eq(LINKED_AT), anyCollection()))
                .thenReturn(List.of(block("GAP")));
        ApiException ex = error(() -> service.signUp(STORE, PICKUP,
                List.of(new PickupRuleGate.Planned(LINKED, LINKED_AT))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LINKED_PICKUP_BLOCKED);
        assertThat(ex.getArgs()).containsEntry("reason", "AUTOMATION_RULE").containsEntry("rule", "GAP");
        assertThat(refused).extracting(PickupRuleGate.RuleBlock::rule).containsExactly("GAP");
    }

    @Test
    void thePickupItselfDuplicatesOrMoreThanFiveLinkedPickupsAreInvalid() {
        PickupRuleGate.Planned other = new PickupRuleGate.Planned(LINKED, LINKED_AT);
        List<List<PickupRuleGate.Planned>> invalid = List.of(
                List.of(new PickupRuleGate.Planned(STORE, PICKUP)),
                List.of(other, other),
                java.util.stream.IntStream.range(0, 6).mapToObj(i -> new PickupRuleGate.Planned(LINKED,
                        LINKED_AT.plus(Duration.ofDays(i)))).toList());
        for (List<PickupRuleGate.Planned> choice : invalid) {
            ApiException ex = error(() -> service.signUp(STORE, PICKUP, choice));
            assertThat(ex.getStatus()).as("%s", choice).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(stores, never()).lock(any());
    }

    @Test
    void aStoreRuleOfTheOwnStoreAnswersPickupNotEligibleAndIsAudited() {
        when(schedule.ruleCheck(eq(store), eq(ME), any(), eq(PICKUP), anyCollection()))
                .thenReturn(List.of(block("GAP")));
        ApiException ex = error(() -> service.signUp(STORE, PICKUP, List.of()));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.PICKUP_NOT_ELIGIBLE);
        assertThat(ex.getArgs()).containsEntry("reason", "AUTOMATION_RULE").containsEntry("rule", "GAP");
        assertThat(refused).hasSize(1);
        verify(signups, never()).save(any());
    }

    @Test
    void theFirstEligibilityReasonAnswersPickupNotEligible() {
        eligible(store, PICKUP, true, PickupEligibilityReason.REGION_RULE_TIMESPAN_LIMIT);
        ApiException ex = error(() -> service.signUp(STORE, PICKUP, List.of()));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.PICKUP_NOT_ELIGIBLE);
        assertThat(ex.getArgs()).containsEntry("reason", "REGION_RULE_TIMESPAN_LIMIT");
    }

    boolean signUpIsTransactional() {
        try {
            return PickupSignupService.class.getMethod("signUp", UUID.class, Instant.class, List.class)
                    .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(ex);
        }
    }

    // ------------------------------------------------------------------ confirm (PU3)

    PickupSignup signup(boolean confirmed) {
        PickupSignup signup = new PickupSignup();
        signup.setStoreId(STORE);
        signup.setUserId(ME);
        signup.setPickupAt(PICKUP);
        signup.setConfirmed(confirmed);
        when(signups.findByStoreIdAndUserIdAndPickupAt(STORE, ME, PICKUP)).thenReturn(Optional.of(signup));
        return signup;
    }

    @Test
    void membersCannotConfirm() {
        signup(false);
        assertThat(error(() -> service.confirm(STORE, PICKUP, ME)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aManagerConfirmsOnceAndThePersonIsBelled() {
        when(rights.rights(any(SessionPrincipal.class), eq(store))).thenReturn(rights(true, true));
        PickupSignup signup = signup(false);
        service.confirm(STORE, PICKUP, ME);
        assertThat(signup.isConfirmed()).isTrue();
        assertThat(signup.getConfirmedAt()).isEqualTo(NOW);
        assertThat(bells()).singleElement().satisfies(bell -> {
            assertThat(bell.request().type()).isEqualTo(NotificationType.PICKUP_CONFIRMED);
            assertThat(bell.request().recipientIds()).isEqualTo(Set.of(ME));
        });
        assertThat(error(() -> service.confirm(STORE, PICKUP, ME)).getCode())
                .isEqualTo(ApiErrorCode.ALREADY_CONFIRMED);
        when(signups.findByStoreIdAndUserIdAndPickupAt(STORE, ME, PICKUP)).thenReturn(Optional.empty());
        assertThat(error(() -> service.confirm(STORE, PICKUP, ME)).getCode()).isEqualTo(ApiErrorCode.SIGNUP_NOT_FOUND);
    }

    // ------------------------------------------------------------------ sign-out / removal (PU5)

    @Test
    void aRemovalBySomeoneElseRingsPickupRemovedWithTheMessageAndMail() {
        UUID other = UUID.randomUUID();
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(MANAGER, UUID.randomUUID(),
                Role.STORE_MANAGER, "manager@test.local"));
        when(rights.mayRemovePickupUser(any(), eq(store), eq(other))).thenReturn(true);
        PickupSignup signup = new PickupSignup();
        when(signups.findByStoreIdAndUserIdAndPickupAt(STORE, other, PICKUP)).thenReturn(Optional.of(signup));
        service.signOut(STORE, PICKUP, other, "  Laden geschlossen ", true);
        verify(signups).delete(signup);
        assertThat(bells()).singleElement().satisfies(bell -> {
            assertThat(bell.request().type()).isEqualTo(NotificationType.PICKUP_REMOVED);
            assertThat(bell.request().recipientIds()).isEqualTo(Set.of(other));
            assertThat(bell.request().params()).containsEntry("message", "Laden geschlossen");
            assertThat(bell.request().mailTemplate()).isEqualTo("pickup/removed");
        });
    }

    @Test
    void aSelfSignOutRingsNobodyAndPastOrUnknownSignupsAreRefused() {
        when(rights.mayRemovePickupUser(any(), eq(store), eq(ME))).thenReturn(true);
        PickupSignup signup = signup(false);
        service.signOut(STORE, PICKUP, ME, null, false);
        verify(signups).delete(signup);
        verify(events, never()).publishEvent(any(StoreNotifications.Send.class));
        assertThat(error(() -> service.signOut(STORE, NOW.minusSeconds(60), ME, null, false)).getCode())
                .isEqualTo(ApiErrorCode.PICKUP_IN_PAST);
        when(signups.findByStoreIdAndUserIdAndPickupAt(STORE, ME, PICKUP)).thenReturn(Optional.empty());
        assertThat(error(() -> service.signOut(STORE, PICKUP, ME, null, false)).getCode())
                .isEqualTo(ApiErrorCode.SIGNUP_NOT_FOUND);
    }

    @Test
    void withoutTheRightNobodyIsRemoved() {
        UUID other = UUID.randomUUID();
        assertThat(error(() -> service.signOut(STORE, PICKUP, other, null, false)).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(signups, never()).delete(any());
    }
}
