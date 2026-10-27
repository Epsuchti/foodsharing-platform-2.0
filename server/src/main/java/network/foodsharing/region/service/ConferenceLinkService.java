package network.foodsharing.region.service;

import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Video conference room per region / working group (plan §13.2 R12): the region's {@code conference_url}, else
 * {@code app.region.conference-url-template} with the slug (upstream: every unit has a conference room).
 */
@Service
public class ConferenceLinkService {

    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final AppProperties properties;

    public ConferenceLinkService(RegionService regions, CurrentActorService currentActor,
                                 PermissionService permissions, AppProperties properties) {
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.properties = properties;
    }

    /** Link without permission check (region page of members). */
    public String linkOf(Region region) {
        if (region.getConferenceUrl() != null && !region.getConferenceUrl().isBlank()) {
            return region.getConferenceUrl();
        }
        return properties.getRegion().getConferenceUrlTemplate().replace("{slug}", region.getSlug());
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND, 403 unless member (or ORGA) */
    @Transactional(readOnly = true)
    public String conferenceUrl(UUID regionOrGroupId) {
        Region region = regions.require(regionOrGroupId);
        permissions.requireMember(currentActor.requirePrincipal(), region.getId());
        return linkOf(region);
    }
}
