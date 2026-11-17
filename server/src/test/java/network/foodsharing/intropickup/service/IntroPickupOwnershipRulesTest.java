package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
import network.foodsharing.intropickup.domain.IntroPickupStoreAlias;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.GuideProfileRepository;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupStoreAliasRepository;
import network.foodsharing.intropickup.repository.PrivateInfoTemplateRepository;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * EinAb ownership and privacy rules IntroPickupApiIT and IntroPickupLinkedPickupIT asserted: only the own guide (or
 * ORGA) changes an EinAb, places are never taken away by an edit, an EinAb goes only to an active guide of the region,
 * a linked EinAb needs the guide's confirmed store pickup; a booking is cancelled only by its own newcomer; the iCal of
 * an EinAb (with its private address) is for its guide, its booked newcomers and ORGA only; store aliases are unique
 * per region by their folded key.
 */
class IntroPickupOwnershipRulesTest {

    static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    static final UUID GUIDE = UUID.randomUUID();
    static final UUID STRANGER = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();
    static final UUID STORE = UUID.randomUUID();

    final IntroPickupRepository pickups = mock(IntroPickupRepository.class);
    final IntroPickupSlotRepository slots = mock(IntroPickupSlotRepository.class);
    final IntroPickupViews views = mock(IntroPickupViews.class);
    final CurrentActorService currentActor = mock(CurrentActorService.class);
    final PermissionService permissions = mock(PermissionService.class);
    final HistoryService history = mock(HistoryService.class);
    final IntroPickupGuideService guides = new IntroPickupGuideService(pickups, slots,
            mock(IntroPickupCommentRepository.class), mock(PrivateInfoTemplateRepository.class), views,
            mock(RegionService.class), mock(RegionPermissionResolver.class), mock(UserService.class), currentActor,
            permissions, history, mock(ApplicationEventPublisher.class), new AppProperties());

    @BeforeEach
    void setUp() {
        when(views.now()).thenReturn(NOW);
        actAs(GUIDE);
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
    }

    void actAs(UUID userId) {
        SessionPrincipal principal = new SessionPrincipal(userId, UUID.randomUUID(), Role.FOODSAVER, "x@test");
        when(currentActor.requirePrincipal()).thenReturn(principal);
        when(currentActor.requireUserId()).thenReturn(userId);
    }

    IntroPickup pickup(int slotCount) {
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.randomUUID());
        pickup.setRegionId(REGION);
        pickup.setGuideId(GUIDE);
        pickup.setCategory(IntroPickupCategory.BAKERY);
        pickup.setStartAt(NOW.plusSeconds(86400));
        pickup.setSlotCount(slotCount);
        pickup.setBusinessName("Beck Wiedikon");
        pickup.setPrivateAddress("Hinterhof 2");
        pickup.setPublicGeneralLocation("Kreis 3");
        when(views.requirePickup(pickup.getId())).thenReturn(pickup);
        return pickup;
    }

    static IntroPickupSlot slot(IntroPickup pickup, SlotStatus status, UUID bookedBy) {
        IntroPickupSlot slot = new IntroPickupSlot();
        slot.setId(UUID.randomUUID());
        slot.setIntroPickupId(pickup.getId());
        slot.setStatus(status);
        slot.setBookingUserId(bookedBy);
        return slot;
    }

    static IntroPickupGuideService.PickupInput change(Integer slotCount, String publicInfo) {
        return new IntroPickupGuideService.PickupInput(null, null, slotCount, null, null, null, null, null, null,
                null, publicInfo, null, null, null);
    }

    static ApiException error(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("no ApiException");
    }

    @Test
    void onlyTheOwnGuideChangesAnEinAb() {
        IntroPickup pickup = pickup(2);
        actAs(STRANGER);
        ApiException ex = error(() -> guides.update(pickup.getId(), change(null, "x")));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ONLY_OWN_INTRO_PICKUPS_MANAGEABLE);
        assertThat(error(() -> guides.assign(pickup.getId(), STRANGER)).getCode())
                .isEqualTo(ApiErrorCode.ONLY_OWN_INTRO_PICKUPS_MANAGEABLE);
        assertThat(pickup.getPublicInfo()).isNull();
    }

    @Test
    void anEditNeverTakesPlacesAway() {
        IntroPickup pickup = pickup(2);
        when(slots.findByPickupForUpdate(pickup.getId())).thenReturn(List.of(
                slot(pickup, SlotStatus.AVAILABLE, null), slot(pickup, SlotStatus.BOOKED, UUID.randomUUID())));
        assertThat(error(() -> guides.update(pickup.getId(), change(1, null))).getCode())
                .isEqualTo(ApiErrorCode.SLOT_COUNT_REDUCTION_NOT_SUPPORTED);
        assertThat(pickup.getSlotCount()).isEqualTo(2);
    }

    @Test
    void anEinAbGoesOnlyToAnActiveGuideOfTheRegion() {
        IntroPickup pickup = pickup(2);
        assertThat(error(() -> guides.assign(pickup.getId(), UUID.randomUUID())).getCode())
                .isEqualTo(ApiErrorCode.ASSIGNABLE_GUIDE_NOT_FOUND);
        assertThat(pickup.getGuideId()).isEqualTo(GUIDE);
    }

    @Test
    void aLinkedEinAbNeedsTheGuidesConfirmedStorePickup() {
        GuideStorePickups storePickups = mock(GuideStorePickups.class);
        guides.setStorePickups(storePickups);
        Instant at = NOW.plusSeconds(3 * 86400);
        IntroPickupGuideService.PickupInput linked = new IntroPickupGuideService.PickupInput(
                IntroPickupCategory.TAKEOUT, at, 2, null, "Tiny Fish", STORE, "Stadelhoferstrasse 10, 8001 Zürich",
                "Zürich", null, null, null, false, false, at);
        ApiException ex = error(() -> guides.create(REGION, linked));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.STORE_PICKUP_NOT_SIGNED_UP);
        verify(storePickups).hasConfirmedSignup(GUIDE, STORE, at);
        verify(pickups, never()).save(any());
    }

    @Test
    void aBookingIsCancelledOnlyByItsOwnNewcomer() {
        IntroPickupBookingService booking = new IntroPickupBookingService(pickups, slots, views,
                mock(RegionService.class), mock(RegionSettingsService.class), mock(RegionPermissionResolver.class),
                mock(UserService.class), currentActor, permissions, history, mock(ApplicationEventPublisher.class),
                mock(IntroPickupCities.class));
        IntroPickup pickup = pickup(2);
        IntroPickupSlot slot = slot(pickup, SlotStatus.BOOKED, UUID.randomUUID());
        when(views.requireSlot(slot.getId(), true)).thenReturn(slot);
        actAs(STRANGER);
        ApiException ex = error(() -> booking.cancelMine(slot.getId()));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ONLY_OWN_BOOKINGS_CANCELLABLE);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void theICalOfAnEinAbIsForItsGuideItsBookedNewcomersAndOrgaOnly() {
        IntroPickupIcalService ical = new IntroPickupIcalService(pickups, slots, mock(GuideProfileRepository.class),
                views, currentActor, permissions, Clock.fixed(NOW, ZoneOffset.UTC));
        IntroPickup pickup = pickup(2);
        UUID booked = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        when(slots.findByPickup(pickup.getId())).thenReturn(List.of(slot(pickup, SlotStatus.BOOKED, booked),
                slot(pickup, SlotStatus.CANCELLED, cancelled)));
        assertThat(ical.pickupIcs(pickup.getId())).contains("BEGIN:VEVENT").contains("Hinterhof 2");
        actAs(booked);
        assertThat(ical.pickupIcs(pickup.getId())).contains("BEGIN:VEVENT");
        for (UUID outsider : List.of(cancelled, STRANGER)) {
            actAs(outsider);
            assertThat(error(() -> ical.pickupIcs(pickup.getId())).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        when(permissions.isOrga(any())).thenReturn(true);
        assertThat(ical.pickupIcs(pickup.getId())).contains("BEGIN:VEVENT");
    }

    @Test
    void storeAliasesAreUniquePerRegionByTheirFoldedKey() {
        IntroPickupStoreAliasRepository aliases = mock(IntroPickupStoreAliasRepository.class);
        IntroPickupAdminService admin = new IntroPickupAdminService(pickups, slots,
                mock(IntroPickupCommentRepository.class), aliases, views, guides, mock(RegionService.class),
                mock(RegionMembersService.class), mock(RegionPermissionResolver.class), mock(UserService.class),
                currentActor, permissions, history, mock(ApplicationEventPublisher.class),
                mock(QuizSessionService.class), mock(MembershipService.class), mock(MemberPickupStats.class));
        IntroPickupStoreAlias existing = new IntroPickupStoreAlias();
        existing.setId(UUID.randomUUID());
        when(aliases.findByRegionIdAndAliasKey(REGION, "babus")).thenReturn(Optional.of(existing));
        when(aliases.save(any())).thenAnswer(inv -> {
            IntroPickupStoreAlias alias = inv.getArgument(0);
            alias.setId(UUID.randomUUID());
            return alias;
        });
        assertThat(error(() -> admin.createAlias(REGION, "Babu's", "X")).getCode())
                .isEqualTo(ApiErrorCode.STORE_ALIAS_EXISTS);
        verify(aliases, never()).save(any());
        IntroPickupStoreAlias created = admin.createAlias(REGION, "Coop Stauffi", " Coop Stauffacher ");
        assertThat(created.getAliasKey()).isEqualTo("coopstauffi");
        assertThat(created.getStoreName()).isEqualTo("Coop Stauffacher");
        assertThat(created.getRegionId()).isEqualTo(REGION);
    }
}
