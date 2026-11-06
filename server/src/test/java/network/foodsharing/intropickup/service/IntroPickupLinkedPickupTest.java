package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.PrivateInfoTemplateRepository;
import network.foodsharing.intropickup.service.IntroPickupCandidateService.Key;
import network.foodsharing.intropickup.service.IntroPickupGuideService.AutoCancelCause;
import network.foodsharing.intropickup.service.IntroPickupGuideService.IntroPickupAutoCancelled;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** EA16 (decision 169): category mapping, candidate rule, link and unlink rules, auto-cancel, no reassign. */
class IntroPickupLinkedPickupTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant PICKUP = Instant.parse("2026-11-06T17:30:00Z");
    private static final UUID GUIDE = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID GUIDE_REGION = UUID.randomUUID();
    private static final UUID SUPERMARKT = UUID.randomUUID();
    private static final UUID HOTEL = UUID.randomUUID();
    private static final SessionPrincipal ACTOR = new SessionPrincipal(GUIDE, UUID.randomUUID(), Role.FOODSAVER,
            "guide@test");

    private final IntroPickupRepository pickups = mock(IntroPickupRepository.class);
    private final IntroPickupSlotRepository slots = mock(IntroPickupSlotRepository.class);
    private final IntroPickupViews views = mock(IntroPickupViews.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final IntroPickupGuideService guides = new IntroPickupGuideService(pickups, slots,
            mock(IntroPickupCommentRepository.class), mock(PrivateInfoTemplateRepository.class), views,
            mock(RegionService.class), mock(RegionPermissionResolver.class), mock(UserService.class), currentActor,
            permissions, history, events, new AppProperties());

    @BeforeEach
    void setUp() {
        when(views.now()).thenReturn(NOW);
        when(currentActor.requirePrincipal()).thenReturn(ACTOR);
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
    }

    // ------------------------------------------------------------------------------------ category map

    @Test
    void storeCategoriesMapByNameAndDefaultToSupermarket() {
        assertThat(StoreCategoryMapping.of("Supermarkt")).isEqualTo(IntroPickupCategory.SUPERMARKET);
        assertThat(StoreCategoryMapping.of("Bio-Laden")).isEqualTo(IntroPickupCategory.SUPERMARKET);
        assertThat(StoreCategoryMapping.of("Bäckerei")).isEqualTo(IntroPickupCategory.BAKERY);
        assertThat(StoreCategoryMapping.of("Restaurant")).isEqualTo(IntroPickupCategory.RESTAURANT);
        assertThat(StoreCategoryMapping.of(" hotel ")).isEqualTo(IntroPickupCategory.RESTAURANT);
        assertThat(StoreCategoryMapping.of("Markt")).isEqualTo(IntroPickupCategory.MARKET);
        assertThat(StoreCategoryMapping.of("Take-away")).isEqualTo(IntroPickupCategory.TAKEOUT);
        assertThat(StoreCategoryMapping.of("Grossverteiler")).isEqualTo(IntroPickupCategory.GROSSABHOLUNG);
        assertThat(StoreCategoryMapping.of("Kiosk")).isEqualTo(IntroPickupCategory.SUPERMARKET);
        assertThat(StoreCategoryMapping.of(null)).isEqualTo(IntroPickupCategory.SUPERMARKET);
        assertThat(StoreCategoryMapping.of(" ")).isEqualTo(IntroPickupCategory.SUPERMARKET);
    }

    // ----------------------------------------------------------------------------------- candidates

    private static Store store(UUID id, UUID regionId, UUID categoryId) {
        Store store = new Store();
        store.setId(id);
        store.setRegionId(regionId);
        store.setName("Migros Limmatplatz");
        store.setStreet("Limmatstrasse 152");
        store.setPostcode("8005");
        store.setCity("Zürich");
        store.setCategoryId(categoryId);
        return store;
    }

    @Test
    void candidatesAreConfirmedUnusedPickupsInGuideRegionsWithThePrefill() {
        UUID subRegion = UUID.randomUUID();
        UUID otherRegion = UUID.randomUUID();
        Store inSub = store(STORE, subRegion, HOTEL);
        Store elsewhere = store(UUID.randomUUID(), otherRegion, SUPERMARKT);
        Store used = store(UUID.randomUUID(), GUIDE_REGION, SUPERMARKT);
        Instant later = PICKUP.plusSeconds(86400);
        List<GuideStorePickups.OwnPickup> own = List.of(
                new GuideStorePickups.OwnPickup(inSub, PICKUP, true),
                new GuideStorePickups.OwnPickup(inSub, later, false),
                new GuideStorePickups.OwnPickup(elsewhere, PICKUP, true),
                new GuideStorePickups.OwnPickup(used, PICKUP, true),
                new GuideStorePickups.OwnPickup(used, later, true));
        Map<UUID, UUID> regionOf = Map.of(subRegion, GUIDE_REGION, GUIDE_REGION, GUIDE_REGION);

        List<IntroPickupCandidateService.Candidate> result = IntroPickupCandidateService.filter(own,
                regionOf::get, Set.of(new Key(used.getId(), PICKUP)), Map.of(HOTEL, "Hotel", SUPERMARKT, "Supermarkt"));

        assertThat(result).hasSize(2);
        IntroPickupCandidateService.Candidate first = result.get(0);
        assertThat(first.storeId()).isEqualTo(STORE);
        assertThat(first.startAt()).isEqualTo(PICKUP);
        assertThat(first.storeName()).isEqualTo("Migros Limmatplatz");
        assertThat(first.address()).isEqualTo("Limmatstrasse 152, 8005 Zürich");
        assertThat(first.city()).isEqualTo("Zürich");
        assertThat(first.category()).isEqualTo(IntroPickupCategory.RESTAURANT);
        assertThat(first.regionId()).isEqualTo(GUIDE_REGION);
        assertThat(result.get(1).storeId()).isEqualTo(used.getId());
        assertThat(result.get(1).startAt()).isEqualTo(later);
    }

    @Test
    void theDeepestGuideRegionAboveTheStoreWins() {
        UUID root = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID district = UUID.randomUUID();
        assertThat(IntroPickupCandidateService.deepestGuideRegion(List.of(root, city, district), Set.of(root, city)))
                .isEqualTo(city);
        assertThat(IntroPickupCandidateService.deepestGuideRegion(List.of(root, city), Set.of(district))).isNull();
    }

    @Test
    void addressLeavesOutEmptyParts() {
        Store store = store(STORE, GUIDE_REGION, null);
        store.setStreet(null);
        assertThat(IntroPickupCandidateService.address(store)).isEqualTo("8005 Zürich");
        store.setPostcode(" ");
        store.setCity(null);
        assertThat(IntroPickupCandidateService.address(store)).isNull();
    }

    // -------------------------------------------------------------------------------- link / unlink

    @Test
    void theLinkIsStoredOnlyWithAStoreAndTheSameStart() {
        assertThat(IntroPickupGuideService.linkOnCreate(PICKUP, PICKUP, STORE)).isEqualTo(PICKUP);
        assertThat(IntroPickupGuideService.linkOnCreate(PICKUP, PICKUP.plusSeconds(60), STORE)).isNull();
        assertThat(IntroPickupGuideService.linkOnCreate(PICKUP, PICKUP, null)).isNull();
        assertThat(IntroPickupGuideService.linkOnCreate(null, PICKUP, STORE)).isNull();
    }

    @Test
    void changingStartOrStoreRemovesTheLinkOtherFieldsKeepIt() {
        assertThat(IntroPickupGuideService.linkAfterUpdate(PICKUP, STORE, PICKUP, STORE, PICKUP)).isEqualTo(PICKUP);
        assertThat(IntroPickupGuideService.linkAfterUpdate(PICKUP, STORE, PICKUP, STORE, PICKUP.plusSeconds(3600)))
                .isNull();
        assertThat(IntroPickupGuideService.linkAfterUpdate(PICKUP, STORE, PICKUP, UUID.randomUUID(), PICKUP)).isNull();
        assertThat(IntroPickupGuideService.linkAfterUpdate(PICKUP, STORE, PICKUP, null, PICKUP)).isNull();
        assertThat(IntroPickupGuideService.linkAfterUpdate(null, STORE, PICKUP, STORE, PICKUP)).isNull();
    }

    // --------------------------------------------------------------------------------- auto-cancel

    private static IntroPickup linked(Instant startAt) {
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.randomUUID());
        pickup.setRegionId(GUIDE_REGION);
        pickup.setGuideId(GUIDE);
        pickup.setCategory(IntroPickupCategory.SUPERMARKET);
        pickup.setStartAt(startAt);
        pickup.setStoreId(STORE);
        pickup.setLinkedPickupAt(startAt);
        pickup.setSlotCount(3);
        return pickup;
    }

    private static IntroPickupSlot slot(SlotStatus status, UUID user) {
        IntroPickupSlot slot = new IntroPickupSlot();
        slot.setId(UUID.randomUUID());
        slot.setStatus(status);
        slot.setBookingUserId(user);
        slot.setGuideId(GUIDE);
        return slot;
    }

    @Test
    void autoCancelCancelsEverySlotSoftDeletesAndTellsTheBookedNewcomers() {
        IntroPickup pickup = linked(PICKUP);
        UUID newcomer = UUID.randomUUID();
        IntroPickupSlot booked = slot(SlotStatus.BOOKED, newcomer);
        IntroPickupSlot free = slot(SlotStatus.AVAILABLE, null);
        when(pickups.findLinked(GUIDE, STORE, PICKUP)).thenReturn(List.of(pickup));
        when(slots.findByPickupForUpdate(pickup.getId())).thenReturn(List.of(booked, free));

        assertThat(guides.autoCancelLinked(GUIDE, STORE, PICKUP, AutoCancelCause.SIGNED_OUT)).isEqualTo(1);

        // CANCELLED never counts against the newcomer (no BOOKED_SLOTS_PREVENT_DELETE guard on this path)
        assertThat(booked.getStatus()).isEqualTo(SlotStatus.CANCELLED);
        assertThat(free.getStatus()).isEqualTo(SlotStatus.CANCELLED);
        assertThat(IntroPickupViews.counts(booked, pickup, NOW)).isFalse();
        assertThat(pickup.getDeletedAt()).isEqualTo(NOW);
        verify(history).record(argThat(e -> e.action() == IntroPickupAction.AUTO_CANCELLED
                && "SIGNED_OUT".equals(String.valueOf(e.metadata().get("cause")))));
        verify(events).publishEvent(new IntroPickupAutoCancelled(pickup.getId(), GUIDE, List.of(newcomer)));
    }

    @Test
    void pastLinkedEinAbsAreNeverTouched() {
        Instant past = NOW.minusSeconds(3600);
        IntroPickup pickup = linked(past);
        when(pickups.findLinked(GUIDE, STORE, past)).thenReturn(List.of(pickup));

        assertThat(guides.autoCancelLinked(GUIDE, STORE, past, AutoCancelCause.AUTOMATION)).isZero();

        assertThat(pickup.getDeletedAt()).isNull();
        verify(slots, never()).findByPickupForUpdate(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------------------------- move sync (PU10)

    @Test
    void aMovedStorePickupMovesTheGuidesLinkedEinAbAndTellsTheBookedNewcomers() {
        IntroPickup pickup = linked(PICKUP);
        UUID newcomer = UUID.randomUUID();
        IntroPickupSlot booked = slot(SlotStatus.BOOKED, newcomer);
        Instant to = PICKUP.plusSeconds(2 * 86400);
        when(pickups.findLinked(GUIDE, STORE, PICKUP)).thenReturn(List.of(pickup));
        when(slots.findByPickup(pickup.getId())).thenReturn(List.of(booked, slot(SlotStatus.AVAILABLE, null)));

        assertThat(guides.moveLinked(List.of(GUIDE, GUIDE), STORE, PICKUP, to)).isEqualTo(1);

        assertThat(pickup.getStartAt()).isEqualTo(to);
        assertThat(pickup.getLinkedPickupAt()).isEqualTo(to);
        // bookings stay; the booking rules are not re-checked
        assertThat(booked.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(booked.getBookingUserId()).isEqualTo(newcomer);
        verify(history).record(argThat(e -> e.action() == IntroPickupAction.UPDATED
                && "startAt".equals(e.metadata().get("fields"))
                && "STORE_PICKUP_MOVED".equals(e.metadata().get("cause"))));
        verify(events).publishEvent(new IntroPickupGuideService.IntroPickupMoved(pickup.getId(), PICKUP, to,
                List.of(newcomer), false));
    }

    @Test
    void onlyTheMovedSignupsGuidesAndOnlyLiveFutureEinAbsMove() {
        IntroPickup past = linked(NOW.minusSeconds(60));
        IntroPickup deleted = linked(PICKUP);
        deleted.setDeletedAt(NOW);
        Instant to = PICKUP.plusSeconds(3600);
        when(pickups.findLinked(GUIDE, STORE, PICKUP)).thenReturn(List.of(past, deleted));

        assertThat(guides.moveLinked(List.of(GUIDE), STORE, PICKUP, to)).isZero();
        // another guide's EinAb at the same pickup is only looked up for the guides that moved
        assertThat(guides.moveLinked(List.of(), STORE, PICKUP, to)).isZero();

        assertThat(past.getStartAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(deleted.getStartAt()).isEqualTo(PICKUP);
        verify(pickups, never()).findLinked(org.mockito.ArgumentMatchers.argThat(id -> !GUIDE.equals(id)), any(),
                any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void lessThan24HoursAheadIsShortNotice() {
        assertThat(IntroPickupGuideService.shortNotice(NOW.plusSeconds(23 * 3600), NOW)).isTrue();
        assertThat(IntroPickupGuideService.shortNotice(NOW.plusSeconds(24 * 3600), NOW)).isFalse();
        IntroPickup pickup = linked(PICKUP);
        Instant soon = NOW.plusSeconds(3600);
        when(pickups.findLinked(GUIDE, STORE, PICKUP)).thenReturn(List.of(pickup));
        when(slots.findByPickup(pickup.getId())).thenReturn(List.of());

        guides.moveLinked(List.of(GUIDE), STORE, PICKUP, soon);

        verify(events).publishEvent(new IntroPickupGuideService.IntroPickupMoved(pickup.getId(), PICKUP, soon,
                List.of(), true));
    }

    // ------------------------------------------------------------------------------------ reassign

    @Test
    void aLinkedEinAbCannotBeHandedToAnotherGuide() {
        IntroPickup pickup = linked(PICKUP);
        when(views.requirePickup(pickup.getId())).thenReturn(pickup);

        assertThatThrownBy(() -> guides.assign(pickup.getId(), UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.LINKED_INTRO_PICKUP_NOT_REASSIGNABLE));
        assertThat(pickup.getGuideId()).isEqualTo(GUIDE);
    }
}
