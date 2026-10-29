package network.foodsharing.region.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Region names for the region system sender of automated messages (AU11, {@link RegionSenderNames}). */
@Service
@Transactional(readOnly = true)
public class RegionSenderNameDirectory implements RegionSenderNames {

    private final RegionRepository regions;

    public RegionSenderNameDirectory(RegionRepository regions) {
        this.regions = regions;
    }

    @Override
    public Optional<String> regionName(UUID regionId) {
        return regionId == null ? Optional.empty()
                : regions.findById(regionId).map(Region::getName).filter(name -> !name.isBlank());
    }
}
