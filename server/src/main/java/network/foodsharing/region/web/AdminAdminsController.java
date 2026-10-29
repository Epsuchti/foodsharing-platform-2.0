package network.foodsharing.region.web;

import network.foodsharing.api.AdminAdminsApi;
import network.foodsharing.api.model.AdminListEntry;
import network.foodsharing.api.model.AdminListPage;
import network.foodsharing.api.model.AdminUnitKind;
import network.foodsharing.api.model.AdministeredUnit;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.AdminListService;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Admins list of all regions and working groups (plan §14.4, AD1, ORGA). */
@RestController
public class AdminAdminsController implements AdminAdminsApi {

    private final AdminListService service;
    private final RegionMapper regions;
    private final UserMapper users;

    public AdminAdminsController(AdminListService service, RegionMapper regions, UserMapper users) {
        this.service = service;
        this.regions = regions;
        this.users = users;
    }

    @Override
    public ResponseEntity<AdminListPage> listAdmins(String q, AdminUnitKind unitKind, Boolean active, Integer page,
                                                    Integer size) {
        Page<AdminListService.AdminEntry> result = service.list(q,
                unitKind == null ? null : AdminListService.UnitKind.valueOf(unitKind.name()), active,
                page == null ? 0 : page, size == null ? 20 : size);
        AdminListPage body = new AdminListPage();
        body.setItems(result.getContent().stream().map(this::toEntry).toList());
        body.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(body);
    }

    private AdminListEntry toEntry(AdminListService.AdminEntry entry) {
        AdminListEntry model = new AdminListEntry();
        model.setUser(users.toSummary(entry.user()));
        model.setEmail(entry.user().getEmail());
        model.setActive(entry.user().isActive());
        model.setLastLoginAt(entry.user().getLastLoginAt() == null ? null
                : TimeMapper.toOffset(entry.user().getLastLoginAt()));
        model.setUnits(entry.units().stream().map(region -> new AdministeredUnit().region(regions.toRef(region)))
                .toList());
        return model;
    }
}
