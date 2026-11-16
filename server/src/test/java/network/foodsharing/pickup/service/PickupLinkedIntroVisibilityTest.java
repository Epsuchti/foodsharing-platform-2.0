package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.intropickup.service.IntroPickupGuideService;
import network.foodsharing.pickup.domain.PickupEligibilityReason;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.pickup.repository.RegularPickupRepository;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * EA16 sign-out warning data (decision 169, was IntroPickupLinkedPickupIT) and the missing link of decision 167 S3
 * (was LinkedBookingFlowIT): a sign-up's linked EinAb and its "link missing until" deadline show in the store's pickup
 * list to the person themself and to the store managers, never to other team members.
 */
class PickupLinkedIntroVisibilityTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final Instant AT = Instant.parse("2026-11-06T17:30:00Z");
    static final UUID STORE = UUID.randomUUID();
    static final UUID GUIDE = UUID.randomUUID();
    static final UUID MEMBER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final UUID EINAB = UUID.randomUUID();

    final StoreService stores = mock(StoreService.class);
    final StorePermissionService rights = mock(StorePermissionService.class);
    final CurrentActorService actor = mock(CurrentActorService.class);
    final IntroPickupGuideService intro = mock(IntroPickupGuideService.class);
    final PickupRuleGate ruleGate = mock(PickupRuleGate.class);
    static final Instant DEADLINE = Instant.parse("2026-11-02T11:00:00Z");
    final Store store = new Store();
    PickupScheduleService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store.setId(STORE);
        store.setPrefetchDays(14);
        when(stores.require(STORE)).thenReturn(store);
        ObjectProvider<IntroPickupGuideService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(intro);
        service = spy(new PickupScheduleService(mock(RegularPickupRepository.class), mock(PickupDateRepository.class),
                mock(PickupSignupRepository.class), mock(PickupExtraSlotRepository.class), stores, rights,
                mock(RegionService.class), mock(RegionOptionsService.class), mock(QuizSessionService.class),
                mock(UserService.class), actor, Clock.fixed(NOW, ZoneOffset.UTC), ruleGate,
                new AppProperties(), provider));
        doReturn(ZoneId.of("Europe/Zurich")).when(service).zoneOf(store);
        doReturn(List.of(new PickupSchedule.Slot(STORE, AT, 3, null, true,
                List.of(signup(GUIDE), signup(MEMBER))))).when(service).schedule(eq(store), any(), any());
        PickupScheduleService.EligibilityContext context = mock(PickupScheduleService.EligibilityContext.class);
        when(context.evaluate(any(), any())).thenReturn(new PickupEligibilityRules.Result(
                List.of(PickupEligibilityReason.ALREADY_SIGNED_UP), null));
        doReturn(context).when(service).context(eq(store), any());
        when(ruleGate.linkMissing(eq(STORE), any(), any())).thenReturn(java.util.Map.of(AT,
                java.util.Map.of(GUIDE, DEADLINE)));
        when(intro.linkedAt(eq(STORE), anyCollection())).thenReturn(List.of(
                new IntroPickupGuideService.LinkedInfo(EINAB, GUIDE, AT, 1)));
    }

    static PickupSignup signup(UUID user) {
        PickupSignup signup = new PickupSignup();
        signup.setId(UUID.randomUUID());
        signup.setUserId(user);
        signup.setPickupAt(AT);
        signup.setConfirmed(true);
        signup.setCreatedAt(NOW.minusSeconds(86400));
        return signup;
    }

    void viewer(UUID userId, boolean manager) {
        SessionPrincipal principal = new SessionPrincipal(userId, UUID.randomUUID(), Role.FOODSAVER, "x@test");
        when(actor.requirePrincipal()).thenReturn(principal);
        when(rights.rights(principal, store)).thenReturn(new StorePermissionService.Rights(true, true, true, false,
                manager, manager, false, false, true, manager, manager, manager, manager, false, false, true, true,
                false, false, manager, false, manager ? UserTeamStatus.COORDINATOR : UserTeamStatus.MEMBER));
    }

    java.util.Map<UUID, IntroPickupGuideService.LinkedInfo> linked() {
        return service.listPickups(STORE, NOW, NOW.plusSeconds(14 * 86400)).getFirst().linkedIntroPickups();
    }

    java.util.Map<UUID, Instant> linkMissing() {
        return service.listPickups(STORE, NOW, NOW.plusSeconds(14 * 86400)).getFirst().linkMissing();
    }

    @Test
    void theGuideSeesTheEinAbLinkedToTheOwnSignup() {
        viewer(GUIDE, false);
        assertThat(linked()).containsOnlyKeys(GUIDE);
        assertThat(linked().get(GUIDE).introPickupId()).isEqualTo(EINAB);
        assertThat(linked().get(GUIDE).bookedCount()).isEqualTo(1);
        assertThat(linkMissing()).containsExactly(java.util.Map.entry(GUIDE, DEADLINE));
    }

    @Test
    void theStoreManagersSeeIt() {
        viewer(MANAGER, true);
        assertThat(linked()).containsOnlyKeys(GUIDE);
        assertThat(linkMissing()).containsOnlyKeys(GUIDE);
    }

    @Test
    void otherTeamMembersDoNot() {
        viewer(MEMBER, false);
        assertThat(linked()).isEmpty();
        assertThat(linkMissing()).isEmpty();
    }
}
