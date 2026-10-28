package network.foodsharing.store.service;

import static network.foodsharing.store.service.StoreServiceTest.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreChainAction;
import network.foodsharing.store.domain.StoreChainKam;
import network.foodsharing.store.domain.StoreChainStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Chain rights (ORGA / STORE_CHAINS admins / KAM / foodsaver), KAM validation and KAM history. */
class StoreChainServiceTest {

    static final UUID CHAIN = UUID.randomUUID();
    StoreChainRepository chains = mock(StoreChainRepository.class);
    StoreChainKamRepository kams = mock(StoreChainKamRepository.class);
    StoreChainAccess access = mock(StoreChainAccess.class);
    UserService users = mock(UserService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    StoreChainService service = new StoreChainService(chains, kams, mock(StoreRepository.class), access, users,
            currentActor, history);
    StoreChain chain = new StoreChain();
    SessionPrincipal actor;

    @BeforeEach
    void setUp() {
        chain.setId(CHAIN);
        chain.setName("Coop");
        chain.setStatus(StoreChainStatus.COOPERATING);
        chain.setNotes("intern");
        when(chains.findById(CHAIN)).thenReturn(Optional.of(chain));
        when(chains.save(any())).thenAnswer(i -> {
            StoreChain c = i.getArgument(0);
            c.setId(CHAIN);
            return c;
        });
        as(Role.FOODSAVER);
    }

    void as(Role role) {
        actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "a@b.c");
        when(currentActor.requirePrincipal()).thenReturn(actor);
        org.mockito.Mockito.doAnswer(i -> {
            if (!network.foodsharing.security.PermissionService.isAtLeast(actor.role(), i.getArgument(0))) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
            return actor;
        }).when(currentActor).requireRole(any());
    }

    static User user(Role role, boolean verified) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setDisplayName("U" + u.getId());
        u.setRole(role);
        u.setVerified(verified);
        return u;
    }

    static StoreChainService.ChainInput input(String name, List<UUID> kamIds) {
        return new StoreChainService.ChainInput(name, null, null, "Basel", "CH", null, null, null, null, null, null,
                kamIds);
    }

    @Test
    void foodsaverReadsWithoutNotesFoodsharerNot() {
        assertThat(service.get(CHAIN).showNotes()).isFalse();
        as(Role.FOODSHARER);
        assertThat(codeOf(() -> service.get(CHAIN))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void onlyChainAdministratorsCreate() {
        assertThat(codeOf(() -> service.create(input("Spar", null)))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(access.mayAdministrateChains(any())).thenReturn(true);
        var view = service.create(input("Spar", null));
        assertThat(view.chain().getStatus()).isEqualTo(StoreChainStatus.WAITING);
        assertThat(view.showNotes()).isTrue();
        verify(history).event(StoreChainAction.CHAIN_CREATED, CHAIN);
        assertThat(codeOf(() -> service.create(input(" ", null)))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void kamMustBeVerifiedStoreManager() {
        when(access.mayAdministrateChains(any())).thenReturn(true);
        User unverified = user(Role.STORE_MANAGER, false);
        User foodsaver = user(Role.FOODSAVER, true);
        User manager = user(Role.STORE_MANAGER, true);
        when(users.findActive(any(java.util.Collection.class))).thenReturn(List.of(unverified, foodsaver, manager));
        assertThat(codeOf(() -> service.update(CHAIN, input(null, List.of(unverified.getId())))))
                .isEqualTo(ApiErrorCode.INVALID_KAM);
        assertThat(codeOf(() -> service.update(CHAIN, input(null, List.of(foodsaver.getId())))))
                .isEqualTo(ApiErrorCode.INVALID_KAM);
        assertThat(codeOf(() -> service.update(CHAIN, input(null, List.of(UUID.randomUUID())))))
                .isEqualTo(ApiErrorCode.INVALID_KAM);
        service.update(CHAIN, input(null, List.of(manager.getId())));
        verify(kams).save(any(StoreChainKam.class));
        verify(history).event(StoreChainAction.KAM_ADDED, CHAIN);
    }

    @Test
    void removedKamIsLogged() {
        when(access.mayAdministrateChains(any())).thenReturn(true);
        StoreChainKam old = new StoreChainKam();
        old.setChainId(CHAIN);
        old.setUserId(UUID.randomUUID());
        when(kams.findByChainId(CHAIN)).thenReturn(List.of(old));
        User manager = user(Role.STORE_MANAGER, true);
        when(users.findActive(any(java.util.Collection.class))).thenReturn(List.of(manager));
        service.update(CHAIN, input(null, List.of(manager.getId())));
        verify(kams).delete(old);
        verify(history).event(StoreChainAction.KAM_REMOVED, CHAIN);
    }

    @Test
    void kamEditsChainButNotKams() {
        as(Role.STORE_MANAGER);
        when(kams.existsByChainIdAndUserId(CHAIN, actor.userId())).thenReturn(true);
        StoreChainKam self = new StoreChainKam();
        self.setChainId(CHAIN);
        self.setUserId(actor.userId());
        when(kams.findByChainId(CHAIN)).thenReturn(List.of(self));
        assertThat(codeOf(() -> service.update(CHAIN, input(null, List.of(actor.userId(), UUID.randomUUID())))))
                .isEqualTo(ApiErrorCode.KAM_CANNOT_EDIT_KAMS);
        service.update(CHAIN, input("Coop Schweiz", List.of(actor.userId())));
        assertThat(chain.getName()).isEqualTo("Coop Schweiz");
        verify(kams, never()).save(any());
        verify(history).event(StoreChainAction.CHAIN_UPDATED, CHAIN);
    }

    @Test
    void othersMayNotEditOrListChainStores() {
        assertThat(codeOf(() -> service.update(CHAIN, input("X", null)))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(() -> service.requireChainStoresAccess(CHAIN))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(() -> service.get(UUID.randomUUID()))).isEqualTo(ApiErrorCode.CHAIN_NOT_FOUND);
    }

    @Test
    void listFiltersByStatusAndSearch() {
        StoreChain migros = new StoreChain();
        migros.setId(UUID.randomUUID());
        migros.setName("Migros");
        migros.setStatus(StoreChainStatus.WAITING);
        when(chains.findAllByOrderByNameAsc()).thenReturn(List.of(chain, migros));
        assertThat(service.list(List.of(StoreChainStatus.WAITING), null, 0, 20).getContent())
                .extracting(v -> v.chain().getName()).containsExactly("Migros");
        assertThat(service.list(null, "coo", 0, 20).getContent()).extracting(v -> v.chain().getName())
                .containsExactly("Coop");
        assertThat(service.list(null, null, 1, 1).getTotalElements()).isEqualTo(2);
    }
}
