package network.foodsharing.region.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.service.RegionDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Region data for the user module ({@link RegionDirectory}). */
@Service
@Transactional(readOnly = true)
public class RegionUserDirectory implements RegionDirectory {

    private final RegionRepository regions;
    private final RegionMembershipRepository memberships;

    public RegionUserDirectory(RegionRepository regions, RegionMembershipRepository memberships) {
        this.regions = regions;
        this.memberships = memberships;
    }

    @Override
    public Optional<RegionInfo> find(UUID regionId) {
        return regions.findById(regionId).map(region -> new RegionInfo(region.getId(), region.getSlug(),
                region.getName(), region.getType().name(), region.getParentId()));
    }

    @Override
    public boolean isActiveMember(UUID userId, UUID regionId) {
        return memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, regionId);
    }

    @Override
    public boolean isAccessible(UUID regionId) {
        return regions.findById(regionId).map(region -> region.getType().isAccessible()).orElse(false);
    }
}
