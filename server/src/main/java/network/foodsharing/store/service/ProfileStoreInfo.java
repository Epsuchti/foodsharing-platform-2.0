package network.foodsharing.store.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreChainKam;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Store facts for the public profile (A6, upstream {@code ProfilePermissions} store rules); read-only. */
@Service
@Transactional(readOnly = true)
public class ProfileStoreInfo {

    /** A live store the user is a team member (MEMBER) of; {@code manager} = store manager. */
    public record TeamStore(UUID id, String name, boolean manager) {
    }

    public record Chain(UUID id, String name) {
    }

    private final StoreMembershipRepository memberships;
    private final StoreRepository stores;
    private final StoreChainKamRepository kams;
    private final StoreChainRepository chains;

    public ProfileStoreInfo(StoreMembershipRepository memberships, StoreRepository stores,
                            StoreChainKamRepository kams, StoreChainRepository chains) {
        this.memberships = memberships;
        this.stores = stores;
        this.kams = kams;
        this.chains = chains;
    }

    /** Live stores where the user is in the team (members incl. managers), sorted by name. */
    public List<TeamStore> teamStores(UUID userId) {
        Map<UUID, StoreMembership> byStore = memberships.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == StoreMembershipStatus.MEMBER)
                .collect(Collectors.toMap(StoreMembership::getStoreId, Function.identity(), (a, b) -> a));
        if (byStore.isEmpty()) {
            return List.of();
        }
        return stores.findLiveIn(byStore.keySet()).stream()
                .map(s -> new TeamStore(s.getId(), s.getName(), byStore.get(s.getId()).isManager()))
                .sorted(Comparator.comparing(TeamStore::name)).toList();
    }

    /** Both users are in the team (or jumper list) of a common live store. */
    public boolean shareTeam(UUID a, UUID b) {
        Set<UUID> ofA = storeIds(a, false);
        ofA.retainAll(storeIds(b, false));
        return !ofA.isEmpty() && !stores.findLiveIn(ofA).isEmpty();
    }

    /** {@code managerId} manages a live store in which {@code userId} is in the team or on the jumper list. */
    public boolean managesStoreOf(UUID managerId, UUID userId) {
        Set<UUID> managed = storeIds(managerId, true);
        managed.retainAll(storeIds(userId, false));
        return !managed.isEmpty() && !stores.findLiveIn(managed).isEmpty();
    }

    /** Number of live stores the user manages ("responsible stores" of the commitments card). */
    public int managedStoreCount(UUID userId) {
        Set<UUID> managed = storeIds(userId, true);
        return managed.isEmpty() ? 0 : stores.findLiveIn(managed).size();
    }

    /** Chains the user is key account manager of. */
    public List<Chain> kamChains(UUID userId) {
        List<UUID> ids = kams.findByUserId(userId).stream().map(StoreChainKam::getChainId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return chains.findAllById(ids).stream().map((StoreChain c) -> new Chain(c.getId(), c.getName()))
                .sorted(Comparator.comparing(Chain::name)).toList();
    }

    private Set<UUID> storeIds(UUID userId, boolean managerOnly) {
        Set<UUID> ids = new HashSet<>();
        for (StoreMembership m : memberships.findByUserId(userId)) {
            boolean team = m.getStatus() == StoreMembershipStatus.MEMBER || m.getStatus() == StoreMembershipStatus.JUMPER;
            if (team && (!managerOnly || (m.isManager() && m.getStatus() == StoreMembershipStatus.MEMBER))) {
                ids.add(m.getStoreId());
            }
        }
        return ids;
    }
}
