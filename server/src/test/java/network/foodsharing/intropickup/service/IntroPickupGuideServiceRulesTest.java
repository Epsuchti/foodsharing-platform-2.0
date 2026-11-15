package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.PrivateInfoTemplateRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * EA10, EA14, EA17: only guides of the region (a paused guide has lost the right) offer EinAbs; past EinAbs are neither
 * edited nor deleted, booked ones are not deleted; a booking is marked done only after the EinAb started.
 */
class IntroPickupGuideServiceRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final UUID GUIDE = UUID.randomUUID();
    private static final UUID REGION = UUID.randomUUID();
    private static final SessionPrincipal ACTOR = new SessionPrincipal(GUIDE, UUID.randomUUID(), Role.FOODSAVER,
            "guide@test");

    private final IntroPickupRepository pickups = mock(IntroPickupRepository.class);
    private final IntroPickupSlotRepository slots = mock(IntroPickupSlotRepository.class);
    private final IntroPickupViews views = mock(IntroPickupViews.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final IntroPickupGuideService guides = new IntroPickupGuideService(pickups, slots,
            mock(IntroPickupCommentRepository.class), mock(PrivateInfoTemplateRepository.class), views,
            mock(RegionService.class), mock(RegionPermissionResolver.class), mock(UserService.class), currentActor,
            permissions, history, mock(ApplicationEventPublisher.class), new AppProperties());

    @BeforeEach
    void setUp() {
        when(views.now()).thenReturn(NOW);
        when(currentActor.requirePrincipal()).thenReturn(ACTOR);
        when(currentActor.requireUserId()).thenReturn(GUIDE);
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
    }

    private IntroPickup own(Instant startAt) {
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.randomUUID());
        pickup.setRegionId(REGION);
        pickup.setGuideId(GUIDE);
        pickup.setCategory(IntroPickupCategory.BAKERY);
        pickup.setStartAt(startAt);
        when(views.requirePickup(pickup.getId())).thenReturn(pickup);
        return pickup;
    }

    private static IntroPickupGuideService.PickupInput input(Instant startAt) {
        return new IntroPickupGuideService.PickupInput(IntroPickupCategory.BAKERY, startAt, 2, null, "Bäckerei", null,
                "Weg 1, 8004 Zürich", "Kreis 4", null, null, null, false, false, null);
    }

    private static void assertCode(Throwable thrown, ApiErrorCode code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void withoutTheGuideRightInTheRegionNoEinAbIsOffered() {
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .require(ACTOR, Permission.CAN_GIVE_INTRO_PICKUPS, REGION);
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                guides.create(REGION, input(NOW.plusSeconds(86400))));
        assertCode(thrown, ApiErrorCode.ACCESS_DENIED);
        verify(pickups, never()).save(any());
    }

    @Test
    void anEinAbCannotStartInThePast() {
        assertThatThrownBy(() -> guides.create(REGION, input(NOW.minusSeconds(60))))
                .satisfies(e -> assertCode(e, ApiErrorCode.INTRO_PICKUP_START_IN_PAST));
    }

    @Test
    void pastEinAbsAreNeitherEditedNorDeleted() {
        IntroPickup past = own(NOW.minusSeconds(3600));
        assertThatThrownBy(() -> guides.update(past.getId(), input(null)))
                .satisfies(e -> assertCode(e, ApiErrorCode.PAST_INTRO_PICKUP_NOT_EDITABLE));
        assertThatThrownBy(() -> guides.delete(past.getId()))
                .satisfies(e -> assertCode(e, ApiErrorCode.PAST_INTRO_PICKUP_NOT_DELETABLE));
    }

    @Test
    void bookedEinAbsAreNotDeleted() {
        IntroPickup upcoming = own(NOW.plusSeconds(86400));
        when(slots.existsByIntroPickupIdAndStatusIn(upcoming.getId(), IntroPickupGuideService.BLOCKING)).thenReturn(true);
        assertThatThrownBy(() -> guides.delete(upcoming.getId()))
                .satisfies(e -> assertCode(e, ApiErrorCode.BOOKED_SLOTS_PREVENT_DELETE));
        assertThat(upcoming.getDeletedAt()).isNull();
    }

    @Test
    void anUnbookedUpcomingEinAbIsDeletedWithItsFreePlaces() {
        IntroPickup upcoming = own(NOW.plusSeconds(86400));
        IntroPickupSlot free = new IntroPickupSlot();
        free.setStatus(SlotStatus.AVAILABLE);
        when(slots.findByPickupForUpdate(upcoming.getId())).thenReturn(List.of(free));
        guides.delete(upcoming.getId());
        assertThat(upcoming.getDeletedAt()).isEqualTo(NOW);
        assertThat(free.getStatus()).isEqualTo(SlotStatus.CANCELLED);
    }

    @Test
    void aBookingIsMarkedDoneOnlyAfterTheStart() {
        IntroPickup upcoming = own(NOW.plusSeconds(3600));
        IntroPickupSlot slot = new IntroPickupSlot();
        slot.setId(UUID.randomUUID());
        slot.setIntroPickupId(upcoming.getId());
        slot.setStatus(SlotStatus.BOOKED);
        slot.setBookingUserId(UUID.randomUUID());
        when(views.requireSlot(slot.getId(), true)).thenReturn(slot);
        assertThatThrownBy(() -> guides.markDone(slot.getId()))
                .satisfies(e -> assertCode(e, ApiErrorCode.FUTURE_APPOINTMENT_NOT_COMPLETABLE));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        upcoming.setStartAt(NOW.minusSeconds(7200));
        guides.markDone(slot.getId());
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.DONE);
        assertThat(slot.getDoneAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> guides.markDone(slot.getId()))
                .satisfies(e -> assertCode(e, ApiErrorCode.ONLY_BOOKED_APPOINTMENTS_COMPLETABLE));
    }
}
