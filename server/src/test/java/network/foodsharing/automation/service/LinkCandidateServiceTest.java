package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

/**
 * Decision 167 S4 (Q3, was LinkCandidateFlowIT): the link picker needs mayEditStore of the own store and two
 * characters; every hit says whether its automation is on and whether the actor may edit it (a bidirectional rule
 * needs that; the save path is PickupPolicyServiceGapTest).
 */
class LinkCandidateServiceTest {

    static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Role.STORE_MANAGER, "manager@test.local");
    static final UUID OWN = UUID.randomUUID();

    PickupPolicyService policies = mock(PickupPolicyService.class);
    PickupPolicyRepository policyRows = mock(PickupPolicyRepository.class);
    StoreQueryService storeQueries = mock(StoreQueryService.class);
    StorePermissionService permissions = mock(StorePermissionService.class);
    LinkCandidateService service;

    @BeforeEach
    void setUp() {
        CurrentActorService actor = mock(CurrentActorService.class);
        when(actor.requirePrincipal()).thenReturn(ACTOR);
        service = new LinkCandidateService(policies, policyRows, storeQueries, permissions, actor);
    }

    static Store store(String name) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setName(name);
        return store;
    }

    @Test
    void withoutTheEditRightOfTheOwnStoreNothingIsSearched() {
        when(policies.requireAutomationStore(OWN)).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.search(OWN, "coop", 0, 20)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(storeQueries, never()).searchKnown(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void theQueryNeedsTwoCharacters() {
        for (String q : new String[] {null, "", " c "}) {
            assertThatThrownBy(() -> service.search(OWN, q, 0, 20)).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getArgs()).containsEntry("field", "q");
            });
        }
        verify(storeQueries, never()).searchKnown(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void hitsCarryTheAutomationStateAndTheEditRight() {
        Store mine = store("Coop Bahnhofbrücke");
        Store foreign = store("Coop Winterthur Bahnhof");
        when(storeQueries.searchKnown("coop", OWN, 0, 20)).thenReturn(new PageImpl<>(List.of(
                new StoreQueryService.KnownStore(mine, List.of("Zürich Stadt")),
                new StoreQueryService.KnownStore(foreign, List.of("Winterthur"))), PageRequest.of(0, 20), 2));
        PickupPolicy enabled = new PickupPolicy();
        enabled.setStoreId(mine.getId());
        enabled.setEnabled(true);
        PickupPolicy disabled = new PickupPolicy();
        disabled.setStoreId(foreign.getId());
        disabled.setEnabled(false);
        when(policyRows.findByStoreIdIn(anyCollection())).thenReturn(List.of(enabled, disabled));
        when(permissions.editableStoreIds(eq(ACTOR), anyCollection())).thenReturn(Set.of(mine.getId()));

        var page = service.search(OWN, "coop", null, null);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(c -> c.store().getName(), LinkCandidateService.Candidate::mayEdit,
                        LinkCandidateService.Candidate::automationEnabled)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Coop Bahnhofbrücke", true, true),
                        org.assertj.core.groups.Tuple.tuple("Coop Winterthur Bahnhof", false, false));
        assertThat(page.getContent().get(1).regionPath()).containsExactly("Winterthur");
    }
}
