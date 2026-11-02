package network.foodsharing.intropickup.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.region.domain.Region;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The city people see on an EinAb before booking it (decision 156, S17 D5): the linked store's city, else the region's
 * name, nothing when online. Free text is never parsed.
 */
@Component
public class IntroPickupCities {

    private final StoreService stores;

    public IntroPickupCities(StoreService stores) {
        this.stores = stores;
    }

    /** The rule. */
    static String city(boolean online, String storeCity, String regionName) {
        if (online) {
            return null;
        }
        return storeCity == null || storeCity.isBlank() ? regionName : storeCity;
    }

    /** City by EinAb id for EinAbs of one region, online ones left out; the stores are loaded in one batch. */
    @Transactional(readOnly = true)
    public Map<UUID, String> of(List<IntroPickup> pickups, Region region) {
        Collection<UUID> storeIds = pickups.stream().filter(p -> !p.isOnline()).map(IntroPickup::getStoreId)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, Store> byId = storeIds.isEmpty() ? Map.of() : stores.storesById(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<UUID, String> cities = new HashMap<>();
        for (IntroPickup pickup : pickups) {
            Store store = pickup.getStoreId() == null ? null : byId.get(pickup.getStoreId());
            String city = city(pickup.isOnline(), store == null ? null : store.getCity(), region.getName());
            if (city != null) {
                cities.put(pickup.getId(), city);
            }
        }
        return cities;
    }
}
