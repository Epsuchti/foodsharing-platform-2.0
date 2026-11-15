package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.automation.domain.AuditKind;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.RequestPolicyAuditRepository;
import network.foodsharing.automation.repository.SignoutCancellationRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who sees which automation decisions (AU8, decision 167): the store scope needs the store's edit right (AU2, via
 * {@link PickupPolicyService#requireAutomationStore}); in the region scope region administrators see every decision,
 * members only those about themselves, outsiders nothing (403) and unverified people an empty page (decision 156).
 */
class AutomationAuditServiceTest {

    static final UUID REGION = UUID.randomUUID();
    static final UUID STORE = UUID.randomUUID();
    static final UUID NO_USER = new UUID(0L, 0L);

    PickupPolicyAuditRepository pickupAudits = mock(PickupPolicyAuditRepository.class);
    RequestPolicyAuditRepository requestAudits = mock(RequestPolicyAuditRepository.class);
    PickupPolicyService pickupPolicies = mock(PickupPolicyService.class);
    StoreService stores = mock(StoreService.class);
    RegionService regions = mock(RegionService.class);
    PermissionService permissions = mock(PermissionService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    StoreVisibility visibility = mock(StoreVisibility.class);
    SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER, "a@local");
    AutomationAuditService service;

    @BeforeEach
    void setUp() {
        service = new AutomationAuditService(pickupAudits, requestAudits, mock(OpenSlotAdvertAuditRepository.class),
                mock(SignoutCancellationRepository.class), mock(PickupAutomationQueries.class), pickupPolicies, stores,
                regions, permissions, currentActor, JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC), visibility);
        Store store = new Store();
        store.setId(STORE);
        store.setRegionId(REGION);
        when(currentActor.requirePrincipal()).thenReturn(actor);
        when(visibility.mayKnowStores(actor)).thenReturn(true);
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION));
        when(regions.zoneOf(REGION)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(stores.liveStoresInRegions(any())).thenReturn(List.of(store));
        when(pickupAudits.search(anyCollection(), anyBoolean(), any(), anyBoolean(), anyCollection(), any(), any(), any()))
                .thenReturn(Page.empty());
    }

    private AutomationAuditService.Query pickups() {
        return new AutomationAuditService.Query(AuditKind.PICKUP, false, null, null, 0, 20);
    }

    @Test
    void regionAdministratorsSeeEveryDecisionOfTheRegionsStores() {
        when(permissions.mayAdministrateRegion(actor, REGION)).thenReturn(true);
        service.regionAudits(REGION, false, pickups());
        verify(pickupAudits).search(eq(List.of(STORE)), eq(true), eq(NO_USER), eq(false), anyCollection(), any(), any(), any());
        // "only mine" narrows it to their own decisions
        service.regionAudits(REGION, true, pickups());
        verify(pickupAudits).search(eq(List.of(STORE)), eq(false), eq(actor.userId()), eq(false), anyCollection(), any(), any(), any());
    }

    @Test
    void membersOnlySeeTheDecisionsAboutThemselves() {
        when(permissions.isMemberOf(actor, REGION)).thenReturn(true);
        service.regionAudits(REGION, false, pickups());
        verify(pickupAudits).search(eq(List.of(STORE)), eq(false), eq(actor.userId()), eq(false), anyCollection(), any(), any(), any());
    }

    @Test
    void outsidersOfTheRegionAreForbidden() {
        assertThatThrownBy(() -> service.regionAudits(REGION, false, pickups())).isInstanceOf(ApiException.class);
        verify(pickupAudits, never()).search(anyCollection(), anyBoolean(), any(), anyBoolean(), anyCollection(), any(), any(), any());
    }

    @Test
    void unverifiedPeopleGetAnEmptyPageWithoutAQuery() {
        when(visibility.mayKnowStores(actor)).thenReturn(false);
        when(permissions.mayAdministrateRegion(actor, REGION)).thenReturn(true);
        assertThat(service.regionAudits(REGION, false, pickups())).isEmpty();
        verify(pickupAudits, never()).search(anyCollection(), anyBoolean(), any(), anyBoolean(), anyCollection(), any(), any(), any());
    }

    @Test
    void theStoreScopeNeedsTheStoresEditRight() {
        when(pickupPolicies.requireAutomationStore(STORE)).thenThrow(ApiException.forbidden(
                network.foodsharing.api.model.ApiErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.storeAudits(STORE, pickups())).isInstanceOf(ApiException.class);
        verify(pickupAudits, never()).search(anyCollection(), anyBoolean(), any(), anyBoolean(), anyCollection(), any(), any(), any());
    }
}
