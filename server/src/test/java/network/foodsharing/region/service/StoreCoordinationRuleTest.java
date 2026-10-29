package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F1-R1: one rule for the stores import and RegionDetail.mayCoordinateStores. */
class StoreCoordinationRuleTest {

    private static final UUID ROOT = UUID.randomUUID();
    private static final UUID REGION = UUID.randomUUID();
    private static final UUID GROUP = UUID.randomUUID();
    private static final List<UUID> CHAIN = List.of(ROOT, REGION);

    private static boolean may(boolean orga, UUID group, Set<UUID> adminOf) {
        return StoreCoordinationRule.mayCoordinate(orga, group, () -> CHAIN, adminOf::contains);
    }

    @Test
    void orgaAlways() {
        assertThat(may(true, GROUP, Set.of())).isTrue();
        assertThat(may(true, null, Set.of())).isTrue();
    }

    @Test
    void aCoordinationGroupSupersedesTheAmbassadors() {
        assertThat(may(false, GROUP, Set.of(GROUP))).isTrue();
        assertThat(may(false, GROUP, Set.of(REGION, ROOT))).isFalse();
    }

    @Test
    void withoutAGroupAmbassadorsOfTheRegionOrAnAncestor() {
        assertThat(may(false, null, Set.of(REGION))).isTrue();
        assertThat(may(false, null, Set.of(ROOT))).isTrue();
        assertThat(may(false, null, Set.of(UUID.randomUUID()))).isFalse();
        assertThat(may(false, null, Set.of(GROUP))).isFalse();
    }

    @Test
    void theChainIsOnlyLoadedWhenNeeded() {
        assertThat(StoreCoordinationRule.mayCoordinate(false, GROUP, () -> {
            throw new AssertionError("chain loaded");
        }, id -> true)).isTrue();
    }
}
