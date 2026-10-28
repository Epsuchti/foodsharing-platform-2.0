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
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Store statistics: months in Europe/Zurich, kg = pickups × average kg, per-member only with mayEditStore. */
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
    CurrentActorService currentActor = mock(CurrentActorService.class);
    StoreStatisticsService service;
    User anna = user("Anna");
    User ben = user("Ben");

    @BeforeEach
    void setUp() {
        service = new StoreStatisticsService(stores, rights, pickupInfo, weights, regions, users, currentActor,
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
    void defaultRangeIsTwelveMonthsUpToNowWithKgAndPerMember() {
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
        assertThat(s.perMember()).extracting(m -> m.user().getDisplayName()).containsExactly("Ben", "Anna");
        assertThat(s.weightCategory().getCode()).isEqualTo("KG_10_20");
    }

    @Test
    void perMemberOnlyWithEditRight() {
        when(rights.rights(any(), any())).thenReturn(rights(true, false));
        assertThat(service.statistics(STORE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)).perMember()).isNull();
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
