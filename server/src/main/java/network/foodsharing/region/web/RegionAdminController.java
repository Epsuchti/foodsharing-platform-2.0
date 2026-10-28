package network.foodsharing.region.web;

import java.util.List;
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
import network.foodsharing.api.model.RegionFunctionUpdateItem;
import network.foodsharing.api.model.RegionsCsvImport;
import network.foodsharing.api.model.RegionsImportPreview;
import network.foodsharing.api.model.WorkingGroupFunction;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.region.domain.RegionJoinMode;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.RegionAdminService;
import network.foodsharing.region.service.RegionsCsvImportService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Region administration API (plan §13.2 R7, ORGA). */
@RestController
public class RegionAdminController implements RegionAdminApi {

    private final RegionAdminService adminService;
    private final RegionsCsvImportService importService;
    private final RegionMapper mapper;

    public RegionAdminController(RegionAdminService adminService, RegionsCsvImportService importService,
                                 RegionMapper mapper) {
        this.adminService = adminService;
        this.importService = importService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<RegionAdminItem> createRegion(RegionForAdministration body) {
        RegionAdminService.RegionInput input = new RegionAdminService.RegionInput(body.getName(), body.getSlug(),
                type(body.getType()), body.getParentId(), body.getEmailName(), body.getTeaser(), body.getDescription(),
                body.getTimeZone(), body.getCountry(), joinMode(body.getJoinMode()), body.getModerated(),
                body.getAmbassadorIds(), functions(body.getFunctions()), body.getReason());
        RegionAdminService.AdminItem created = adminService.create(input);
        if (body.getTeamPage() != null) {
            created = adminService.setTeamPage(created.region().getId(), body.getTeamPage(), body.getReason());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAdminItem(created));
    }

    @Override
    public ResponseEntity<Void> deleteRegion(UUID regionId, ReasonBody reasonBody, Boolean force) {
        adminService.delete(regionId, Boolean.TRUE.equals(force), reasonBody.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RegionAdminItem> getRegionForAdmin(UUID regionId) {
        return ResponseEntity.ok(mapper.toAdminItem(adminService.get(regionId)));
    }

    @Override
    public ResponseEntity<RegionsImportPreview> importRegionsCsv(RegionsCsvImport body) {
        return ResponseEntity.ok(mapper.toPreview(importService.importCsv(body.getCsv(),
                Boolean.TRUE.equals(body.getDryRun()))));
    }

    @Override
    public ResponseEntity<RegionAdminPage> listRegionsForAdmin(UUID parentId, String q, Integer page, Integer size) {
        Page<RegionAdminService.AdminItem> items = adminService.list(parentId, q, page == null ? 0 : page,
                size == null ? 50 : size);
        RegionAdminPage result = new RegionAdminPage();
        result.setItems(items.getContent().stream().map(mapper::toAdminItem).toList());
        result.setPage(PageMapper.toPageMeta(items));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<RegionAdminItem> moveRegion(UUID regionId, MoveRegionRequest body) {
        return ResponseEntity.ok(mapper.toAdminItem(adminService.move(regionId, body.getNewParentId(),
                body.getReason())));
    }

    @Override
    public ResponseEntity<Void> removeRegionFunction(UUID regionId, WorkingGroupFunction function) {
        adminService.removeFunction(regionId, RegionMapper.function(function));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RegionFunctionAssignment> setRegionFunction(UUID regionId, WorkingGroupFunction function,
                                                                      RegionFunctionUpdate body) {
        return ResponseEntity.ok(mapper.toAssignment(adminService.setFunction(regionId, RegionMapper.function(function),
                body.getGroupId(), body.getReason())));
    }

    @Override
    public ResponseEntity<RegionAdminItem> updateRegion(UUID regionId, RegionAdminUpdate body) {
        RegionAdminService.RegionInput input = new RegionAdminService.RegionInput(body.getName(), body.getSlug(),
                type(body.getType()), null, body.getEmailName(), body.getTeaser(), body.getDescription(),
                body.getTimeZone(), body.getCountry(), joinMode(body.getJoinMode()), body.getModerated(),
                body.getAmbassadorIds(), functions(body.getFunctions()), body.getReason());
        RegionAdminService.AdminItem updated = adminService.update(regionId, input);
        if (body.getTeamPage() != null) {
            updated = adminService.setTeamPage(regionId, body.getTeamPage(), body.getReason());
        }
        return ResponseEntity.ok(mapper.toAdminItem(updated));
    }

    private static UnitType type(network.foodsharing.api.model.UnitType type) {
        return type == null ? null : UnitType.valueOf(type.name());
    }

    private static RegionJoinMode joinMode(network.foodsharing.api.model.RegionJoinMode mode) {
        return mode == null ? null : RegionJoinMode.valueOf(mode.name());
    }

    private static List<RegionAdminService.FunctionInput> functions(List<RegionFunctionUpdateItem> items) {
        return items == null ? null : items.stream()
                .map(item -> new RegionAdminService.FunctionInput(RegionMapper.function(item.getFunction()),
                        item.getGroupId())).toList();
    }
}
