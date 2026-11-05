package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.quiz.service.GuideQuizEligibility;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.Permission;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Q4: the pickup gate of the EINAB_GUIDE quiz (threshold of the home region, default 25, guides exempt). */
class GuideQuizGateTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID HOME = UUID.randomUUID();

    private final MemberPickupStats stats = mock(MemberPickupStats.class);
    private final RegionService regions = mock(RegionService.class);
    private final RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    private final UserService users = mock(UserService.class);
    private final GuideQuizGate gate = new GuideQuizGate(stats, regions, resolver, users);
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(USER);
        user.setHomeRegionId(HOME);
        when(users.require(USER)).thenReturn(user);
        Region home = new Region();
        home.setId(HOME);
        home.setGuideQuizMinPickups(10);
        when(regions.find(HOME)).thenReturn(Optional.of(home));
        when(resolver.permissionsByRegion(USER)).thenReturn(Map.of());
    }

    @Test
    void theHomeRegionSetsTheThreshold() {
        when(stats.pickupCount(USER)).thenReturn(9);
        GuideQuizEligibility.Status below = gate.of(USER);
        assertThat(below).isEqualTo(new GuideQuizEligibility.Status(9, 10, false));
        assertThat(below.reached()).isFalse();
        when(stats.pickupCount(USER)).thenReturn(10);
        assertThat(gate.of(USER).reached()).isTrue();
    }

    @Test
    void withoutAHomeRegionTheGlobalDefaultApplies() {
        user.setHomeRegionId(null);
        when(stats.pickupCount(USER)).thenReturn(24);
        assertThat(gate.of(USER)).isEqualTo(new GuideQuizEligibility.Status(24, 25, false));
        when(stats.pickupCount(USER)).thenReturn(25);
        assertThat(gate.of(USER).reached()).isTrue();
    }

    @Test
    void releasedGuidesSkipTheThreshold() {
        when(stats.pickupCount(USER)).thenReturn(0);
        when(resolver.permissionsByRegion(USER)).thenReturn(Map.of(UUID.randomUUID(),
                Set.of(Permission.CAN_MANAGE_USERS), UUID.randomUUID(), Set.of(Permission.CAN_GIVE_INTRO_PICKUPS)));
        GuideQuizEligibility.Status status = gate.of(USER);
        assertThat(status.guide()).isTrue();
        assertThat(status.reached()).isTrue();
    }
}
