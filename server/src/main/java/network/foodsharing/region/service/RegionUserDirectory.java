package network.foodsharing.region.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.service.RegionDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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

    private static final String REQUEST_CACHE = RegionUserDirectory.class.getName() + ".prefetched";

    @Override
    public Optional<RegionInfo> find(UUID regionId) {
        Map<UUID, RegionInfo> prefetched = requestCache(false);
        if (prefetched != null && prefetched.containsKey(regionId)) {
            return Optional.of(prefetched.get(regionId));
        }
        return regions.findById(regionId).map(RegionUserDirectory::info);
    }

    @Override
    public void prefetch(Collection<UUID> regionIds) {
        Map<UUID, RegionInfo> cache = requestCache(true);
        if (cache == null) {
            return;
        }
        List<UUID> missing = regionIds.stream().filter(Objects::nonNull).distinct()
                .filter(id -> !cache.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            regions.findByIdIn(missing).forEach(region -> cache.put(region.getId(), info(region)));
        }
    }

    private static RegionInfo info(Region region) {
        return new RegionInfo(region.getId(), region.getSlug(), region.getName(), region.getType().name(),
                region.getParentId());
    }

    /** Request-scoped (never shared across requests, so no stale names); null outside a web request. */
    @SuppressWarnings("unchecked")
    private static Map<UUID, RegionInfo> requestCache(boolean create) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Map<UUID, RegionInfo> cache = (Map<UUID, RegionInfo>) attributes.getAttribute(REQUEST_CACHE,
                RequestAttributes.SCOPE_REQUEST);
        if (cache == null && create) {
            cache = new HashMap<>();
            attributes.setAttribute(REQUEST_CACHE, cache, RequestAttributes.SCOPE_REQUEST);
        }
        return cache;
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
