package network.foodsharing.region.service;

import java.util.Objects;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the closure table and {@code has_children} when regions are created, moved or removed (plan §13.2 R1).
 * Permission checks are the caller's job (region administration is ORGA only, T12).
 */
@Service
public class RegionTreeService {

    private final RegionRepository regions;
    private final RegionClosureRepository closure;
    private final HistoryService history;

    public RegionTreeService(RegionRepository regions, RegionClosureRepository closure, HistoryService history) {
        this.regions = regions;
        this.closure = closure;
        this.history = history;
    }

    /**
     * Adds a newly saved region to the tree (closure rows, parent's {@code has_children}).
     *
     * @throws ApiException 404 REGION_NOT_FOUND when the parent does not exist
     */
    @Transactional
    @NoHistory(reason = "part of creating a region; the caller records RegionAction.CREATED / GroupAction.CREATED")
    public void attach(Region region) {
        if (region.getParentId() != null) {
            Region parent = regions.findById(region.getParentId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
            parent.setHasChildren(true);
        }
        regions.flush();
        closure.insertLeaf(region.getId(), region.getParentId());
    }

    /**
     * Moves a region (with its subtree) below a new parent and records MOVED (field parentId).
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 409 CANNOT_MOVE_INTO_DESCENDANT (incl. moving below itself)
     */
    @Transactional
    public Region move(UUID regionId, UUID newParentId, String reason) {
        Region region = regions.findById(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        if (newParentId != null) {
            regions.findById(newParentId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
            if (closure.isAncestorOrSelf(regionId, newParentId)) {
                throw ApiException.conflict(ApiErrorCode.CANNOT_MOVE_INTO_DESCENDANT);
            }
        }
        UUID oldParentId = region.getParentId();
        if (Objects.equals(oldParentId, newParentId)) {
            return region;
        }
        history.record(history.event(region.isGroup() ? network.foodsharing.region.domain.GroupAction.UPDATED
                        : RegionAction.MOVED, regionId)
                .change("parentId", oldParentId, newParentId).reason(reason).region(regionId).build());
        region.setParentId(newParentId);
        regions.flush();
        closure.move(regionId, newParentId);
        refreshHasChildren(oldParentId);
        refreshHasChildren(newParentId);
        return region;
    }

    /**
     * Removes a leaf from the tree (closure rows go with the region via ON DELETE CASCADE) and refreshes the
     * parent's {@code has_children}.
     */
    @Transactional
    @NoHistory(reason = "part of deleting a region; the caller records RegionAction.DELETED")
    public void detachAndDelete(Region region) {
        UUID parentId = region.getParentId();
        regions.delete(region);
        regions.flush();
        refreshHasChildren(parentId);
    }

    private void refreshHasChildren(UUID regionId) {
        if (regionId != null) {
            regions.findById(regionId).ifPresent(parent -> parent.setHasChildren(regions.countByParentId(regionId) > 0));
        }
    }
}
