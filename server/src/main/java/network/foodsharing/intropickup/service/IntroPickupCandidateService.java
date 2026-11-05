package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EinAb candidates from the guide's own store pickups (EA16, decision 169): every future <b>confirmed</b> sign-up
 * (no horizon) at a store that lies in, or below, a region where the guide gives EinAbs, minus the pickups that
 * already have a live EinAb of the guide with the same store and start. Each carries the pre-fill of the form.
 */
@Service
public class IntroPickupCandidateService {

    /** One candidate: the store pickup plus the pre-fill values (category mapped, guide region found). */
    public record Candidate(UUID storeId, String storeName, Instant startAt, String address, String city,
                            IntroPickupCategory category, UUID regionId) {
    }

    /** Store + start of a live EinAb (hides used pickups). */
    record Key(UUID storeId, Instant startAt) {
    }

    private final GuideStorePickups storePickups;
    private final IntroPickupGuideService guides;
    private final IntroPickupRepository pickups;
    private final RegionService regions;
    private final StoreCategoryService categories;
    private final CurrentActorService currentActor;

    public IntroPickupCandidateService(GuideStorePickups storePickups, IntroPickupGuideService guides,
                                       IntroPickupRepository pickups, RegionService regions,
                                       StoreCategoryService categories, CurrentActorService currentActor) {
        this.storePickups = storePickups;
        this.guides = guides;
        this.pickups = pickups;
        this.regions = regions;
        this.categories = categories;
        this.currentActor = currentActor;
    }

    /** @throws network.foodsharing.common.error.ApiException 403 ACCESS_DENIED unless a guide somewhere */
    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        guides.requireGuideAnywhere();
        UUID guideId = currentActor.requireUserId();
        Set<UUID> guideRegions = guides.guideRegions().stream().map(Region::getId).collect(Collectors.toSet());
        List<GuideStorePickups.OwnPickup> own = storePickups.myUpcoming();
        if (guideRegions.isEmpty() || own.isEmpty()) {
            return List.of();
        }
        Set<Key> used = pickups.findByGuide(guideId).stream().filter(p -> p.getStoreId() != null)
                .map(IntroPickupCandidateService::keyOf).collect(Collectors.toSet());
        Map<UUID, String> categoryNames = categories.listCategories().stream().map(StoreCategoryService.Item::item)
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName(), (a, b) -> a));
        Map<UUID, UUID> regionCache = new HashMap<>();
        Function<UUID, UUID> guideRegionOf = storeRegionId -> regionCache.computeIfAbsent(storeRegionId,
                id -> deepestGuideRegion(regions.ancestors(id).stream().map(Region::getId).toList(), guideRegions));
        return filter(own, guideRegionOf, used, categoryNames);
    }

    /**
     * The rule (unit-tested): confirmed only, a guide region found for the store, not used yet; in input order.
     * {@code guideRegionOf} maps the store's region to the guide region that contains it, or null.
     */
    static List<Candidate> filter(List<GuideStorePickups.OwnPickup> own, Function<UUID, UUID> guideRegionOf,
                                  Set<Key> used, Map<UUID, String> categoryNames) {
        List<Candidate> result = new ArrayList<>();
        for (GuideStorePickups.OwnPickup pickup : own) {
            Store store = pickup.store();
            if (!pickup.confirmed() || store == null || used.contains(new Key(store.getId(), pickup.pickupAt()))) {
                continue;
            }
            UUID regionId = guideRegionOf.apply(store.getRegionId());
            if (regionId == null) {
                continue;
            }
            String categoryName = store.getCategoryId() == null ? null : categoryNames.get(store.getCategoryId());
            result.add(new Candidate(store.getId(), store.getName(), pickup.pickupAt(), address(store),
                    blankToNull(store.getCity()), StoreCategoryMapping.of(categoryName), regionId));
        }
        return result;
    }

    /** The deepest of the store region's ancestors (root first, itself last) in which the user is a guide. */
    static UUID deepestGuideRegion(List<UUID> ancestorsRootFirst, Collection<UUID> guideRegions) {
        for (int i = ancestorsRootFirst.size() - 1; i >= 0; i--) {
            if (guideRegions.contains(ancestorsRootFirst.get(i))) {
                return ancestorsRootFirst.get(i);
            }
        }
        return null;
    }

    /** "street, postcode city" without empty parts; null when nothing is known. */
    static String address(Store store) {
        String place = Stream.of(store.getPostcode(), store.getCity()).map(IntroPickupCandidateService::blankToNull)
                .filter(Objects::nonNull).collect(Collectors.joining(" "));
        String joined = Stream.of(blankToNull(store.getStreet()), blankToNull(place)).filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static Key keyOf(IntroPickup pickup) {
        return new Key(pickup.getStoreId(), pickup.getStartAt());
    }
}
