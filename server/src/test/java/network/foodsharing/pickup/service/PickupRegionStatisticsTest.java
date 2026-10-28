package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.service.RegionStatisticsContributor;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** Region statistics (B5): pickups and saved kg of the last calendar month (Europe/Zurich) in the subtree. */
class PickupRegionStatisticsTest {

    static final UUID REGION = UUID.randomUUID();
    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StoreService stores = mock(StoreService.class);

    @SuppressWarnings("unchecked")
    RegionStatisticsContributor contributor(Instant now) {
        ObjectProvider<StoreService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(stores);
        return new PickupStatsIntegrations().pickupRegionStatistics(signups, provider, Clock.fixed(now, ZoneOffset.UTC));
    }

    static Store store(String weight) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setWeightCategory(weight);
        return store;
    }

    static PickupSignup signup(Store store) {
        PickupSignup s = new PickupSignup();
        s.setStoreId(store.getId());
        s.setUserId(UUID.randomUUID());
        return s;
    }

    @Test
    void kgIsTheSumOfTheStoresAverageKgPerConfirmedSignup() {
        Store big = store("KG_10_20");
        Store small = store("KG_3_5");
        Store unset = store(null);
        when(stores.liveStoresInRegions(Set.of(REGION))).thenReturn(List.of(big, small, unset));
        when(stores.averageKg("KG_10_20")).thenReturn(15.0);
        when(stores.averageKg("KG_3_5")).thenReturn(4.0);
        when(stores.averageKg(null)).thenReturn(0.0);
        // last calendar month in Europe/Zurich: 2026-10-01T00:00+02:00 .. 2026-11-01T00:00+01:00
        when(signups.findConfirmedInStores(any(), eq(Instant.parse("2026-09-30T22:00:00Z")),
                eq(Instant.parse("2026-10-31T23:00:00Z"))))
                .thenReturn(List.of(signup(big), signup(big), signup(small), signup(unset), signup(small)));
        RegionStatistics statistics = new RegionStatistics();
        contributor(Instant.parse("2026-11-15T10:00:00Z")).contribute(REGION, Set.of(REGION), statistics);
        assertThat(statistics.getPickupsLastMonth()).isEqualTo(5);
        assertThat(statistics.getSavedFoodWeightLastMonth()).isEqualTo(38.0);
    }

    @Test
    void monthBoundaryUsesZurichTime() {
        Store store = store("KG_1_3");
        when(stores.liveStoresInRegions(Set.of(REGION))).thenReturn(List.of(store));
        when(stores.averageKg("KG_1_3")).thenReturn(2.0);
        // 2026-10-31T23:30Z is already November in Zurich, so the last month is October
        when(signups.findConfirmedInStores(any(), eq(Instant.parse("2026-09-30T22:00:00Z")),
                eq(Instant.parse("2026-10-31T23:00:00Z")))).thenReturn(List.of(signup(store)));
        RegionStatistics statistics = new RegionStatistics();
        contributor(Instant.parse("2026-10-31T23:30:00Z")).contribute(REGION, Set.of(REGION), statistics);
        assertThat(statistics.getSavedFoodWeightLastMonth()).isEqualTo(2.0);
    }

    @Test
    void noStoresMeansZero() {
        when(stores.liveStoresInRegions(Set.of(REGION))).thenReturn(List.of());
        RegionStatistics statistics = new RegionStatistics();
        statistics.setPickupsLastMonth(7);
        contributor(Instant.parse("2026-11-15T10:00:00Z")).contribute(REGION, Set.of(REGION), statistics);
        assertThat(statistics.getPickupsLastMonth()).isZero();
        assertThat(statistics.getSavedFoodWeightLastMonth()).isZero();
    }
}
