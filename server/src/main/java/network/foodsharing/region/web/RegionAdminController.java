package network.foodsharing.region.web;

import java.util.UUID;
import network.foodsharing.api.RegionAdminApi;
import network.foodsharing.api.model.MoveRegionRequest;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.RegionAdminItem;
import network.foodsharing.api.model.RegionAdminPage;
import network.foodsharing.api.model.RegionAdminUpdate;
import network.foodsharing.api.model.RegionForAdministration;
import network.foodsharing.api.model.RegionFunctionAssignment;
import network.foodsharing.api.model.RegionFunctionUpdate;
import network.foodsharing.api.model.RegionsCsvImport;
import network.foodsharing.api.model.RegionsImportPreview;
import network.foodsharing.api.model.WorkingGroupFunction;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Region administration API (implemented in T12).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class RegionAdminController implements RegionAdminApi {

    @Override
    public ResponseEntity<RegionAdminItem> createRegion(RegionForAdministration regionForAdministration) {
        throw ApiException.notImplemented("createRegion");
    }

    @Override
    public ResponseEntity<Void> deleteRegion(UUID regionId, ReasonBody reasonBody, Boolean force) {
        throw ApiException.notImplemented("deleteRegion");
    }

    @Override
    public ResponseEntity<RegionAdminItem> getRegionForAdmin(UUID regionId) {
        throw ApiException.notImplemented("getRegionForAdmin");
    }

    @Override
    public ResponseEntity<RegionsImportPreview> importRegionsCsv(RegionsCsvImport regionsCsvImport) {
        throw ApiException.notImplemented("importRegionsCsv");
    }

    @Override
    public ResponseEntity<RegionAdminPage> listRegionsForAdmin(UUID parentId, String q, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionsForAdmin");
    }

    @Override
    public ResponseEntity<RegionAdminItem> moveRegion(UUID regionId, MoveRegionRequest moveRegionRequest) {
        throw ApiException.notImplemented("moveRegion");
    }

    @Override
    public ResponseEntity<Void> removeRegionFunction(UUID regionId, WorkingGroupFunction function) {
        throw ApiException.notImplemented("removeRegionFunction");
    }

    @Override
    public ResponseEntity<RegionFunctionAssignment> setRegionFunction(UUID regionId, WorkingGroupFunction function, RegionFunctionUpdate regionFunctionUpdate) {
        throw ApiException.notImplemented("setRegionFunction");
    }

    @Override
    public ResponseEntity<RegionAdminItem> updateRegion(UUID regionId, RegionAdminUpdate regionAdminUpdate) {
        throw ApiException.notImplemented("updateRegion");
    }
}
