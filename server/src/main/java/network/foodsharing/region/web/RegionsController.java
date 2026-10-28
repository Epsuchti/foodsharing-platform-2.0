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
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.HistoryContext;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.ConferenceLinkService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.region.service.RegionStatisticsService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Regions API (plan §13.2 R2–R13, AD1 member management). */
@RestController
public class RegionsController implements RegionsApi {

    private final MembershipService membershipService;
    private final RegionReadService readService;
    private final RegionMembersService membersService;
    private final RegionOptionsService optionsService;
    private final RegionSettingsService settingsService;
    private final RegionStatisticsService statisticsService;
    private final ConferenceLinkService conferenceService;
    private final RegionService regionService;
    private final HistoryQueryService historyQueryService;
    private final HistoryMapper historyMapper;
    private final CurrentActorService currentActorService;
    private final RegionMapper mapper;

    public RegionsController(MembershipService membershipService, RegionReadService readService,
                             RegionMembersService membersService, RegionOptionsService optionsService,
                             RegionSettingsService settingsService, RegionStatisticsService statisticsService,
                             ConferenceLinkService conferenceService, RegionService regionService,
                             HistoryQueryService historyQueryService, HistoryMapper historyMapper,
                             CurrentActorService currentActorService, RegionMapper mapper) {
        this.membershipService = membershipService;
        this.readService = readService;
        this.membersService = membersService;
        this.optionsService = optionsService;
        this.settingsService = settingsService;
        this.statisticsService = statisticsService;
        this.conferenceService = conferenceService;
        this.regionService = regionService;
        this.historyQueryService = historyQueryService;
        this.historyMapper = historyMapper;
        this.currentActorService = currentActorService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<RegionDetail> getRegion(UUID regionId) {
        return ResponseEntity.ok(mapper.toDetail(readService.detail(regionId)));
    }

    @Override
    public ResponseEntity<ConferenceLink> getRegionConference(UUID regionId) {
        ConferenceLink link = new ConferenceLink();
        link.setUrl(conferenceService.conferenceUrl(regionId));
        return ResponseEntity.ok(link);
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getRegionHistory(UUID regionId, List<String> actions, OffsetDateTime from,
                                                             OffsetDateTime to, Integer page, Integer size) {
        regionService.require(regionId);
        HistoryFilter filter = new HistoryFilter(SubjectType.REGION, regionId, null, null, actions,
                TimeMapper.toInstant(from), TimeMapper.toInstant(to), page == null ? 0 : page,
                size == null ? 20 : size);
        return ResponseEntity.ok(historyMapper.toPage(historyQueryService.list(filter)));
    }

    @Override
    public ResponseEntity<RegionOptionPermissions> getRegionOptionPermissions(UUID regionId) {
        return ResponseEntity.ok(mapper.toOptionPermissions(optionsService.optionPermissions(regionId)));
    }

    @Override
    public ResponseEntity<RegionOptions> getRegionOptions(UUID regionId) {
        return ResponseEntity.ok(mapper.toOptions(optionsService.options(regionId)));
    }

    @Override
    public ResponseEntity<RegionPin> getRegionPin(UUID regionId) {
        return ResponseEntity.ok(mapper.toPin(optionsService.pin(regionId)));
    }

    @Override
    public ResponseEntity<RegionSettings> getRegionSettings(UUID regionId) {
        return ResponseEntity.ok(mapper.toSettings(settingsService.settings(regionId)));
    }

    @Override
    public ResponseEntity<RegionStatistics> getRegionStatistics(UUID regionId) {
        return ResponseEntity.ok(mapper.toStatistics(statisticsService.view(regionId)));
    }

    @Override
    public ResponseEntity<RegionPermissionGrant> grantRegionPermission(UUID regionId, UUID userId,
                                                                       Permission permission) {
        return ResponseEntity.ok(mapper.toGrant(membersService.grant(regionId, userId, permission(permission))));
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
        return ResponseEntity.ok(readService.children(regionId, Boolean.TRUE.equals(includeGroups)).stream()
                .map(mapper::toRef).toList());
    }

    @Override
    public ResponseEntity<RegionMemberPage> listRegionMembers(UUID regionId, String search, Boolean verified, Role role,
                                                              Boolean admin, Integer page, Integer size) {
        Page<RegionMembersService.MemberView> members = membersService.members(regionId, search, verified, role, admin,
                page == null ? 0 : page, size == null ? 20 : size);
        RegionMemberPage result = new RegionMemberPage();
        result.setItems(mapper.toMembers(members.getContent()));
        result.setPage(PageMapper.toPageMeta(members));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<List<RegionPermissionGrant>> listRegionPermissions(UUID regionId) {
        return ResponseEntity.ok(membersService.grants(regionId).stream().map(mapper::toGrant).toList());
    }

    @Override
    public ResponseEntity<Void> removeRegionAdmin(UUID regionId, UUID userId) {
        membersService.removeAdmin(regionId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeRegionMember(UUID regionId, UUID userId, ReasonBody reasonBody) {
        membersService.removeMember(regionId, userId, reasonBody.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> revokeRegionPermission(UUID regionId, UUID userId, Permission permission) {
        membersService.revoke(regionId, userId, permission(permission));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RegionRefPage> searchRegions(String q, List<UnitType> types, Integer page, Integer size) {
        List<network.foodsharing.region.domain.UnitType> filter = types == null ? List.of() : types.stream()
                .map(type -> network.foodsharing.region.domain.UnitType.valueOf(type.name())).toList();
        Page<network.foodsharing.region.domain.Region> result = readService.search(q, filter, page == null ? 0 : page,
                size == null ? 20 : size);
        RegionRefPage model = new RegionRefPage();
        model.setItems(result.getContent().stream().map(mapper::toRef).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<UnitMembership> setHomeRegion(HomeRegionUpdate homeRegionUpdate) {
        MembershipService.Unit unit = HistoryContext.withReason(homeRegionUpdate.getReason(), () ->
                membershipService.setHomeRegion(currentActorService.requireUserId(), homeRegionUpdate.getRegionId()));
        return ResponseEntity.ok(mapper.toUnit(unit));
    }

    @Override
    public ResponseEntity<RegionMember> setRegionAdmin(UUID regionId, UUID userId) {
        return ResponseEntity.ok(mapper.toMember(membersService.setAdmin(regionId, userId)));
    }

    @Override
    public ResponseEntity<RegionOptions> updateRegionOptions(UUID regionId, RegionOptionsPatch regionOptionsPatch) {
        return ResponseEntity.ok(mapper.toOptions(optionsService.update(regionId, mapper.toChange(regionOptionsPatch))));
    }

    @Override
    public ResponseEntity<RegionPin> updateRegionPin(UUID regionId, RegionPin regionPin) {
        return ResponseEntity.ok(mapper.toPin(optionsService.updatePin(regionId, mapper.fromPin(regionPin))));
    }

    @Override
    public ResponseEntity<RegionSettings> updateRegionSettings(UUID regionId, RegionSettingsPatch regionSettingsPatch) {
        return ResponseEntity.ok(mapper.toSettings(
                settingsService.update(regionId, mapper.toChange(regionSettingsPatch))));
    }

    private static network.foodsharing.security.Permission permission(Permission permission) {
        return network.foodsharing.security.Permission.valueOf(permission.name());
    }
}
