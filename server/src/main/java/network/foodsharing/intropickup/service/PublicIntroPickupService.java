package network.foodsharing.intropickup.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.UpcomingIntroPickups;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public view of upcoming EinAbs with free places (no guide, no private address, no call link): the public list of a
 * region and the "next EinAbs" on the public region page.
 */
@Service
public class PublicIntroPickupService implements UpcomingIntroPickups {

    private final IntroPickupRepository pickups;
    private final IntroPickupViews views;
    private final RegionService regions;

    public PublicIntroPickupService(IntroPickupRepository pickups, IntroPickupViews views, RegionService regions) {
        this.pickups = pickups;
        this.views = views;
        this.regions = regions;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Page<PublicIntroPickupView> list(String slug, IntroPickupCategory category, Boolean visitFairteiler,
                                            Integer page, Integer size) {
        Region region = regions.requireBySlug(slug);
        List<PublicIntroPickupView> all = upcoming(region).stream()
                .filter(v -> category == null || v.category().equals(category.name()))
                .filter(v -> visitFairteiler == null || v.visitFairteiler() == visitFairteiler).toList();
        return IntroPickupViews.page(all, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicIntroPickupView> next(UUID regionId, int limit) {
        return regions.find(regionId).map(region -> upcoming(region).stream().limit(limit).toList())
                .orElse(List.of());
    }

    private List<PublicIntroPickupView> upcoming(Region region) {
        return views.pickups(pickups.findUpcoming(region.getId(), views.now())).stream().map(view -> {
            int free = (int) view.slots().stream().filter(s -> s.slot().getStatus() == SlotStatus.AVAILABLE).count();
            return new PublicIntroPickupView(view.pickup().getId(), view.pickup().getCategory().name(),
                    view.pickup().getStartAt(), view.pickup().getPublicGeneralLocation(),
                    view.pickup().getBusinessName(), view.pickup().getPublicInfo(), view.pickup().isVisitFairteiler(),
                    view.pickup().isOnline() && free > 0 ? 1 : free, region.getSlug());
        }).filter(v -> v.freeSlots() > 0).toList();
    }
}
