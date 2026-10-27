package network.foodsharing.region.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read API of the region tree for other modules (docs/conventions.md A15: {@link #zoneOf}). */
@Service
public class RegionService {

    private final RegionRepository regions;
    private final RegionClosureRepository closure;
    private final AppProperties properties;

    public RegionService(RegionRepository regions, RegionClosureRepository closure, AppProperties properties) {
        this.regions = regions;
        this.closure = closure;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Optional<Region> find(UUID regionId) {
        return regionId == null ? Optional.empty() : regions.findById(regionId);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Region require(UUID regionId) {
        return find(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Region requireBySlug(String slug) {
        return regions.findBySlug(slug).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Region> findAll(Collection<UUID> regionIds) {
        return regionIds.isEmpty() ? List.of() : regions.findByIdIn(regionIds);
    }

    /** Time zone of a region (its own, else {@code app.region.default-time-zone}). */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID regionId) {
        return find(regionId).map(region -> zone(region.getTimeZone()))
                .orElseGet(() -> zone(properties.getRegion().getDefaultTimeZone()));
    }

    /** The region's ancestors including itself, root first. */
    @Transactional(readOnly = true)
    public List<Region> ancestors(UUID regionId) {
        return regions.findAncestors(regionId);
    }

    /** Ids of the region and all regions / groups below it. */
    @Transactional(readOnly = true)
    public List<UUID> subtreeIds(UUID regionId) {
        return closure.subtree(regionId);
    }

    /** Direct children, regions before working groups, by sort order and name. */
    @Transactional(readOnly = true)
    public List<Region> children(UUID regionId, boolean includeGroups) {
        List<Region> children = regionId == null ? regions.findByParentIdIsNullOrderBySortOrderAscNameAsc()
                : regions.findByParentIdOrderBySortOrderAscNameAsc(regionId);
        return children.stream().filter(region -> includeGroups || !region.isGroup()).toList();
    }

    public static boolean isValidZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException | NullPointerException e) {
            return false;
        }
    }

    private ZoneId zone(String zone) {
        return isValidZone(zone) ? ZoneId.of(zone) : ZoneId.of("Europe/Zurich");
    }
}
