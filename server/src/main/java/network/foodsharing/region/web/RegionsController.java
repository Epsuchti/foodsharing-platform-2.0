package network.foodsharing.region.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.RegionsApi;
import network.foodsharing.api.model.ConferenceLink;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.HomeRegionUpdate;
import network.foodsharing.api.model.Permission;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.RegionDetail;
import network.foodsharing.api.model.RegionMember;
import network.foodsharing.api.model.RegionMemberPage;
import network.foodsharing.api.model.RegionOptionPermissions;
import network.foodsharing.api.model.RegionOptions;
import network.foodsharing.api.model.RegionOptionsPatch;
import network.foodsharing.api.model.RegionPermissionGrant;
import network.foodsharing.api.model.RegionPin;
import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.RegionRefPage;
import network.foodsharing.api.model.RegionSettings;
import network.foodsharing.api.model.RegionSettingsPatch;
import network.foodsharing.api.model.RegionStatistics;
import network.foodsharing.api.model.Role;
import network.foodsharing.api.model.UnitMembership;
import network.foodsharing.api.model.UnitType;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryContext;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regions API (T12 implements the rest; T11 implemented join/leave/home region).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class RegionsController implements RegionsApi {

    private final MembershipService membershipService;
    private final CurrentActorService currentActorService;
    private final RegionMapper mapper;

    public RegionsController(MembershipService membershipService, CurrentActorService currentActorService,
                             RegionMapper mapper) {
        this.membershipService = membershipService;
        this.currentActorService = currentActorService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<RegionDetail> getRegion(UUID regionId) {
        throw ApiException.notImplemented("getRegion");
    }

    @Override
    public ResponseEntity<ConferenceLink> getRegionConference(UUID regionId) {
        throw ApiException.notImplemented("getRegionConference");
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getRegionHistory(UUID regionId, List<String> actions, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        throw ApiException.notImplemented("getRegionHistory");
    }

    @Override
    public ResponseEntity<RegionOptionPermissions> getRegionOptionPermissions(UUID regionId) {
        throw ApiException.notImplemented("getRegionOptionPermissions");
    }

    @Override
    public ResponseEntity<RegionOptions> getRegionOptions(UUID regionId) {
        throw ApiException.notImplemented("getRegionOptions");
    }

    @Override
    public ResponseEntity<RegionPin> getRegionPin(UUID regionId) {
        throw ApiException.notImplemented("getRegionPin");
    }

    @Override
    public ResponseEntity<RegionSettings> getRegionSettings(UUID regionId) {
        throw ApiException.notImplemented("getRegionSettings");
    }

    @Override
    public ResponseEntity<RegionStatistics> getRegionStatistics(UUID regionId) {
        throw ApiException.notImplemented("getRegionStatistics");
    }

    @Override
    public ResponseEntity<RegionPermissionGrant> grantRegionPermission(UUID regionId, UUID userId, Permission permission) {
        throw ApiException.notImplemented("grantRegionPermission");
    }

    @Override
    public ResponseEntity<UnitMembership> joinRegion(UUID regionId) {
        return ResponseEntity.ok(mapper.toUnit(membershipService.join(currentActorService.requireUserId(), regionId)));
    }

    @Override
    public ResponseEntity<Void> leaveRegion(UUID regionId) {
        membershipService.leave(currentActorService.requireUserId(), regionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<UnitMembership>> listMyUnits() {
        return ResponseEntity.ok(membershipService.units(currentActorService.requireUserId()).stream()
                .map(mapper::toUnit).toList());
    }

    @Override
    public ResponseEntity<List<RegionRef>> listRegionChildren(UUID regionId, Boolean includeGroups) {
        throw ApiException.notImplemented("listRegionChildren");
    }

    @Override
    public ResponseEntity<RegionMemberPage> listRegionMembers(UUID regionId, String search, Boolean verified, Role role, Boolean admin, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionMembers");
    }

    @Override
    public ResponseEntity<List<RegionPermissionGrant>> listRegionPermissions(UUID regionId) {
        throw ApiException.notImplemented("listRegionPermissions");
    }

    @Override
    public ResponseEntity<Void> removeRegionAdmin(UUID regionId, UUID userId) {
        throw ApiException.notImplemented("removeRegionAdmin");
    }

    @Override
    public ResponseEntity<Void> removeRegionMember(UUID regionId, UUID userId, ReasonBody reasonBody) {
        throw ApiException.notImplemented("removeRegionMember");
    }

    @Override
    public ResponseEntity<Void> revokeRegionPermission(UUID regionId, UUID userId, Permission permission) {
        throw ApiException.notImplemented("revokeRegionPermission");
    }

    @Override
    public ResponseEntity<RegionRefPage> searchRegions(String q, List<UnitType> types, Integer page, Integer size) {
        throw ApiException.notImplemented("searchRegions");
    }

    @Override
    public ResponseEntity<UnitMembership> setHomeRegion(HomeRegionUpdate homeRegionUpdate) {
        MembershipService.Unit unit = HistoryContext.withReason(homeRegionUpdate.getReason(), () ->
                membershipService.setHomeRegion(currentActorService.requireUserId(), homeRegionUpdate.getRegionId()));
        return ResponseEntity.ok(mapper.toUnit(unit));
    }

    @Override
    public ResponseEntity<RegionMember> setRegionAdmin(UUID regionId, UUID userId) {
        throw ApiException.notImplemented("setRegionAdmin");
    }

    @Override
    public ResponseEntity<RegionOptions> updateRegionOptions(UUID regionId, RegionOptionsPatch regionOptionsPatch) {
        throw ApiException.notImplemented("updateRegionOptions");
    }

    @Override
    public ResponseEntity<RegionPin> updateRegionPin(UUID regionId, RegionPin regionPin) {
        throw ApiException.notImplemented("updateRegionPin");
    }

    @Override
    public ResponseEntity<RegionSettings> updateRegionSettings(UUID regionId, RegionSettingsPatch regionSettingsPatch) {
        throw ApiException.notImplemented("updateRegionSettings");
    }
}
