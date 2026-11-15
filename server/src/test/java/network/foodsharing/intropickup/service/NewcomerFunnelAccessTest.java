package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.service.NewcomerProgressService.Funnel;
import network.foodsharing.intropickup.service.NewcomerProgressService.FunnelStep;
import network.foodsharing.intropickup.service.NewcomerProgressService.Progress;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.region.service.RegionSettingsService.IntroPickupSettings;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** EA21: the newcomer funnel shows names to user managers, counts only to guides, and nothing to other members. */
class NewcomerFunnelAccessTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final UUID REGION = UUID.randomUUID();
    private static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Role.FOODSAVER, "actor@test");

    private final RegionService regions = mock(RegionService.class);
    private final RegionSettingsService settings = mock(RegionSettingsService.class);
    private final MembershipService memberships = mock(MembershipService.class);
    private final IntroPickupViews views = mock(IntroPickupViews.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final NewcomerProgressService service = new NewcomerProgressService(regions, settings,
            mock(RegionPermissionResolver.class), memberships, mock(IntroPickupRepository.class),
            mock(IntroPickupSlotRepository.class), views, users, currentActor, permissions, new AppProperties());
    private final User newcomer = new User();

    @BeforeEach
    void setUp() {
        Region region = new Region();
        when(regions.require(REGION)).thenReturn(region);
        RegionSettingsService.Settings regionSettings = mock(RegionSettingsService.Settings.class);
        when(regionSettings.introPickups()).thenReturn(new IntroPickupSettings(3, true, true, true, Set.of("ONLINE")));
        when(settings.read(region)).thenReturn(regionSettings);
        when(views.now()).thenReturn(NOW);
        when(currentActor.requirePrincipal()).thenReturn(ACTOR);
        newcomer.setId(UUID.randomUUID());
        newcomer.setRegisteredAt(NOW.minus(Duration.ofDays(3)));
        when(memberships.membersOf(REGION)).thenReturn(List.of(newcomer.getId()));
        when(users.findActive(anyCollection())).thenReturn(List.of(newcomer));
    }

    private void grant(boolean manageUsers, boolean giveIntroPickups) {
        when(permissions.has(ACTOR, Permission.CAN_MANAGE_USERS, REGION)).thenReturn(manageUsers);
        when(permissions.has(ACTOR, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).thenReturn(giveIntroPickups);
    }

    private static List<Progress> names(Funnel funnel) {
        return funnel.steps().stream().flatMap(step -> step.users().stream()).toList();
    }

    private static int people(Funnel funnel) {
        return funnel.steps().stream().mapToInt(FunnelStep::userCount).sum();
    }

    @Test
    void userManagersSeeTheNames() {
        grant(true, false);
        Funnel funnel = service.funnel(REGION, null);
        assertThat(people(funnel)).isEqualTo(1);
        assertThat(names(funnel)).extracting(progress -> progress.user().getId()).containsExactly(newcomer.getId());
    }

    @Test
    void guidesSeeCountsOnly() {
        grant(false, true);
        Funnel funnel = service.funnel(REGION, null);
        assertThat(people(funnel)).isEqualTo(1);
        assertThat(names(funnel)).isEmpty();
    }

    @Test
    void otherMembersAreRefused() {
        grant(false, false);
        assertThatThrownBy(() -> service.funnel(REGION, null)).isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }
}
