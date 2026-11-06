package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.intropickup.service.GuideStorePickups;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreVisibility;
import network.foodsharing.user.domain.User;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pickup side of EinAb candidates and links (EA16, decision 169); replaces the no-op default of intropickup. Same
 * rows and store visibility as {@link PickupUserService#myPickups()}, without the schedule (only store and time are
 * needed), and with few dependencies so the intropickup services can use it without a bean cycle.
 */
@Service
@Primary
public class GuideStorePickupsImpl implements GuideStorePickups {

    private final PickupSignupRepository signups;
    private final StoreService stores;
    private final CurrentActorService currentActor;
    private final Clock clock;

    public GuideStorePickupsImpl(PickupSignupRepository signups, StoreService stores,
                                 CurrentActorService currentActor, Clock clock) {
        this.signups = signups;
        this.stores = stores;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnPickup> myUpcoming() {
        User user = currentActor.requireUser();
        if (!StoreVisibility.mayKnow(user)) {
            return List.of();
        }
        List<PickupSignup> rows = signups.findByUserIdAndPickupAtGreaterThanEqualOrderByPickupAtAscIdAsc(user.getId(),
                clock.instant());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Store> live = stores.liveStores(rows.stream().map(PickupSignup::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        return rows.stream().filter(row -> live.containsKey(row.getStoreId()))
                .map(row -> new OwnPickup(live.get(row.getStoreId()), row.getPickupAt(), row.isConfirmed())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConfirmedSignup(UUID userId, UUID storeId, Instant pickupAt) {
        return signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt).filter(PickupSignup::isConfirmed)
                .isPresent();
    }
}
