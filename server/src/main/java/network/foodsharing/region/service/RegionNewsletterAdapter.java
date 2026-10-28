package network.foodsharing.region.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.notification.service.NewsletterRegions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Region data for newsletters (N7). */
@Component
@Transactional(readOnly = true)
public class RegionNewsletterAdapter implements NewsletterRegions {

    private final RegionService regions;

    public RegionNewsletterAdapter(RegionService regions) {
        this.regions = regions;
    }

    @Override
    public Optional<RegionSummary> find(UUID regionId) {
        return regions.find(regionId).map(r -> new RegionSummary(r.getId(), r.getSlug(), r.getName(),
                r.getType().name(), r.getParentId()));
    }

    @Override
    public List<UUID> memberIdsOfSubtree(UUID regionId) {
        return regions.memberIdsOfSubtree(regionId);
    }

    @Override
    public Set<UUID> adminRegionIds(UUID userId) {
        return regions.adminRegionIds(userId);
    }
}
