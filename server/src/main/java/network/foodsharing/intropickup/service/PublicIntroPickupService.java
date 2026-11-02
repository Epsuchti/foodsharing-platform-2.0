package network.foodsharing.intropickup.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.UpcomingIntroPickups;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public view of upcoming EinAbs with free places (date, online or on site, city, free places; S17 D5): the public list of a
 * region and the "next EinAbs" on the public region page.
 */
@Service
public class PublicIntroPickupService implements UpcomingIntroPickups {

    private final IntroPickupRepository pickups;
    private final IntroPickupViews views;
    private final RegionService regions;
    private final IntroPickupCities cities;

    public PublicIntroPickupService(IntroPickupRepository pickups, IntroPickupViews views, RegionService regions,
                                    IntroPickupCities cities) {
        this.cities = cities;
        this.pickups = pickups;
        this.views = views;
        this.regions = regions;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Page<PublicIntroPickupView> list(String slug, Integer page, Integer size) {
        return IntroPickupViews.page(upcoming(regions.requireBySlug(slug)), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicIntroPickupView> next(UUID regionId, int limit) {
        return regions.find(regionId).map(region -> upcoming(region).stream().limit(limit).toList())
                .orElse(List.of());
    }

    private List<PublicIntroPickupView> upcoming(Region region) {
        List<IntroPickup> upcoming = pickups.findUpcoming(region.getId(), views.now());
        Map<UUID, String> cityById = cities.of(upcoming, region);
        return views.pickups(upcoming).stream().map(view -> {
            int free = (int) view.slots().stream().filter(s -> s.slot().getStatus() == SlotStatus.AVAILABLE).count();
            return new PublicIntroPickupView(view.pickup().getId(), view.pickup().getStartAt(),
                    view.pickup().isOnline(), cityById.get(view.pickup().getId()),
                    view.pickup().isOnline() && free > 0 ? 1 : free, region.getSlug());
        }).filter(v -> v.freeSlots() > 0).toList();
    }
}
