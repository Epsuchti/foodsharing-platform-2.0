package network.foodsharing.store.service;

import static network.foodsharing.store.service.StoreServiceTest.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.PickupWeightCategory;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Store statistics: months in Europe/Zurich, kg = pickups × average kg, paged per-member only with mayEditStore. */
class StoreStatisticsServiceTest {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();

    StoreService stores = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    StorePickupInfo pickupInfo = mock(StorePickupInfo.class);
    PickupWeightCategoryRepository weights = mock(PickupWeightCategoryRepository.class);
    RegionService regions = mock(RegionService.class);
    UserService users = mock(UserService.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    StoreStatisticsService service;
    User anna = user("Anna");
    User ben = user("Ben");

    @BeforeEach
    void setUp() {
        service = new StoreStatisticsService(stores, rights, pickupInfo, weights, regions, users, memberships, currentActor,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Store store = new Store();
        store.setId(STORE);
        store.setRegionId(REGION);
        store.setWeightCategory("KG_10_20");
        when(stores.require(STORE)).thenReturn(store);
        when(regions.zoneOf(REGION)).thenReturn(ZURICH);
        PickupWeightCategory weight = new PickupWeightCategory();
        weight.setCode("KG_10_20");
        weight.setAverageKg(new BigDecimal("15.00"));
        when(weights.findByCode("KG_10_20")).thenReturn(Optional.of(weight));
        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                Role.FOODSAVER, "a@b.c"));
        when(users.findAll(any())).thenReturn(List.of(anna, ben));
    }

    static User user(String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setDisplayName(name);
        return u;
    }

    static StorePermissionService.Rights rights(boolean access, boolean edit) {
        return new StorePermissionService.Rights(access, access, access, false, edit, edit, false, edit, access, edit,
                edit, edit, edit, false, false, false, false, false, false, false, false, UserTeamStatus.MEMBER);
    }

    @Test
    void monthBoundariesFollowZurichTime() {
        List<StorePickupInfo.PastSignup> rows = List.of(
                new StorePickupInfo.PastSignup(anna.getId(), Instant.parse("2026-09-30T22:30:00Z")), // Oct 1 00:30
                new StorePickupInfo.PastSignup(anna.getId(), Instant.parse("2026-09-30T21:30:00Z")), // Sep 30 23:30
                new StorePickupInfo.PastSignup(ben.getId(), Instant.parse("2026-10-31T23:30:00Z"))); // Nov 1 00:30
        List<StoreStatisticsService.Month> months = StoreStatisticsService.months(rows, ZURICH, YearMonth.of(2026, 9),
                YearMonth.of(2026, 11), 15);
        assertThat(months).extracting(StoreStatisticsService.Month::pickups).containsExactly(1, 1, 1);
        assertThat(months).extracting(StoreStatisticsService.Month::kg).containsExactly(15.0, 15.0, 15.0);
    }

    @Test
    void defaultRangeIsTwelveMonthsUpToNowWithKg() {
        when(rights.rights(any(), any())).thenReturn(rights(true, true));
        when(pickupInfo.confirmedSignups(eq(STORE), eq(Instant.parse("2025-11-30T23:00:00Z")), eq(NOW)))
                .thenReturn(List.of(new StorePickupInfo.PastSignup(anna.getId(), Instant.parse("2026-10-10T17:00:00Z")),
                        new StorePickupInfo.PastSignup(ben.getId(), Instant.parse("2026-10-12T17:00:00Z")),
                        new StorePickupInfo.PastSignup(ben.getId(), Instant.parse("2026-11-01T17:00:00Z"))));
        var s = service.statistics(STORE, null, null);
        assertThat(s.totalPickups()).isEqualTo(3);
        assertThat(s.totalKg()).isEqualTo(45.0);
        assertThat(s.months()).hasSize(12);
        assertThat(s.months().get(10).pickups()).isEqualTo(2);
        assertThat(s.months().get(11).month()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(s.weightCategory().getCode()).isEqualTo("KG_10_20");
    }

    @Test
    void perMemberOnlyWithEditRight() {
        when(rights.rights(any(), any())).thenReturn(rights(true, false));
        assertThat(codeOf(() -> service.memberPickups(STORE, null, null, query(null, null, null, 0))))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    static TeamListQuery query(TeamListQuery.Status status, String search, TeamListQuery.Sort sort, int page) {
        return new TeamListQuery(status, search, sort, page, 2);
    }

    static StoreMembership membership(User user, StoreMembershipStatus status, boolean manager) {
        StoreMembership m = new StoreMembership();
        m.setStoreId(STORE);
        m.setUserId(user.getId());
        m.setStatus(status);
        m.setManager(manager);
        return m;
    }

    static StorePickupInfo.PastSignup signup(User user, String at) {
        return new StorePickupInfo.PastSignup(user.getId(), Instant.parse(at));
    }

    /** Anna manager (2 pickups), Ben jumper (1, latest), Chloé former (2), Dora applicant = former (1). */
    private void perMemberFixture(User chloe, User dora) {
        when(rights.rights(any(), any())).thenReturn(rights(true, true));
        when(users.findAll(any())).thenReturn(List.of(anna, ben, chloe, dora));
        when(memberships.findByStoreId(STORE)).thenReturn(List.of(membership(anna, StoreMembershipStatus.MEMBER, true),
                membership(ben, StoreMembershipStatus.JUMPER, false),
                membership(dora, StoreMembershipStatus.APPLIED, false)));
        when(pickupInfo.confirmedSignups(eq(STORE), any(), any())).thenReturn(List.of(
                signup(anna, "2026-03-01T08:00:00Z"), signup(anna, "2026-05-01T08:00:00Z"),
                signup(ben, "2026-06-01T08:00:00Z"),
                signup(chloe, "2026-02-01T08:00:00Z"), signup(chloe, "2026-04-01T08:00:00Z"),
                signup(dora, "2026-01-15T08:00:00Z")));
    }

    @Test
    void perMemberStatusLastPickupAndDefaultSortByCount() {
        User chloe = user("Chloé");
        User dora = user("Dora");
        perMemberFixture(chloe, dora);
        var page = service.memberPickups(STORE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                new TeamListQuery(null, null, null, 0, 20));
        // ties (2 pickups) by name: Anna before Chloé; then Ben before Dora
        assertThat(page.getContent()).extracting(m -> m.user().getDisplayName())
                .containsExactly("Anna", "Chloé", "Ben", "Dora");
        assertThat(page.getContent()).extracting(StoreStatisticsService.MemberPickups::status).containsExactly(
                TeamListQuery.Status.MEMBER, TeamListQuery.Status.FORMER, TeamListQuery.Status.JUMPER,
                TeamListQuery.Status.FORMER);
        assertThat(page.getContent().getFirst().manager()).isTrue();
        assertThat(page.getContent().getFirst().lastPickupAt()).isEqualTo(Instant.parse("2026-05-01T08:00:00Z"));
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void perMemberFiltersSortsAndPages() {
        User chloe = user("Chloé");
        User dora = user("Dora");
        perMemberFixture(chloe, dora);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        assertThat(service.memberPickups(STORE, from, to, query(TeamListQuery.Status.FORMER, null, null, 0))
                .getContent()).extracting(m -> m.user().getDisplayName()).containsExactly("Chloé", "Dora");
        assertThat(service.memberPickups(STORE, from, to, query(TeamListQuery.Status.MANAGER, null, null, 0))
                .getContent()).extracting(m -> m.user().getDisplayName()).containsExactly("Anna");
        assertThat(service.memberPickups(STORE, from, to, query(TeamListQuery.Status.JUMPER, null, null, 0))
                .getContent()).extracting(m -> m.user().getDisplayName()).containsExactly("Ben");
        assertThat(service.memberPickups(STORE, from, to, query(TeamListQuery.Status.MEMBER, null, null, 0))
                .getContent()).extracting(m -> m.user().getDisplayName()).containsExactly("Anna");
        // accent- and case-insensitive search
        assertThat(service.memberPickups(STORE, from, to, query(null, "CHLOE", null, 0)).getContent())
                .extracting(m -> m.user().getDisplayName()).containsExactly("Chloé");
        // last pickup in the range, newest first
        assertThat(service.memberPickups(STORE, from, to, new TeamListQuery(null, null, TeamListQuery.Sort.LAST_PICKUP,
                0, 20)).getContent()).extracting(m -> m.user().getDisplayName())
                .containsExactly("Ben", "Anna", "Chloé", "Dora");
        // NAME: managers first, then name; page 2 of size 2
        var second = service.memberPickups(STORE, from, to, query(null, null, TeamListQuery.Sort.NAME, 1));
        assertThat(second.getContent()).extracting(m -> m.user().getDisplayName()).containsExactly("Chloé", "Dora");
        assertThat(second.getTotalPages()).isEqualTo(2);
        assertThat(service.memberPickups(STORE, from, to, query(null, null, TeamListQuery.Sort.NAME, 5)).getContent())
                .isEmpty();
    }

    @Test
    void perMemberCountsOnlyTheRange() {
        when(rights.rights(any(), any())).thenReturn(rights(true, true));
        when(pickupInfo.confirmedSignups(eq(STORE), eq(Instant.parse("2026-02-28T23:00:00Z")),
                eq(Instant.parse("2026-03-31T22:00:00Z")))).thenReturn(List.of(signup(anna, "2026-03-10T08:00:00Z")));
        var page = service.memberPickups(STORE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                new TeamListQuery(null, null, null, 0, 20));
        assertThat(page.getContent()).singleElement().satisfies(m -> {
            assertThat(m.pickups()).isEqualTo(1);
            assertThat(m.status()).isEqualTo(TeamListQuery.Status.FORMER);
        });
        assertThat(codeOf(() -> service.memberPickups(STORE, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1),
                new TeamListQuery(null, null, null, 0, 20)))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void needsAccessAndAnOrderedRange() {
        when(rights.rights(any(), any())).thenReturn(rights(false, false));
        assertThat(codeOf(() -> service.statistics(STORE, null, null))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(rights.rights(any(), any())).thenReturn(rights(true, false));
        assertThat(codeOf(() -> service.statistics(STORE, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }
}
