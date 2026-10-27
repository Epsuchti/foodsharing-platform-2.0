package network.foodsharing.region.mapper;

import network.foodsharing.api.model.ApplyType;
import network.foodsharing.api.model.GroupApplication;
import network.foodsharing.api.model.GroupApplicationStatus;
import network.foodsharing.api.model.GroupCategory;
import network.foodsharing.api.model.GroupMember;
import network.foodsharing.api.model.GroupMembershipState;
import network.foodsharing.api.model.GroupRequirementCheck;
import network.foodsharing.api.model.GroupRequirements;
import network.foodsharing.api.model.WorkingGroupCreate;
import network.foodsharing.api.model.WorkingGroupDetail;
import network.foodsharing.api.model.WorkingGroupSummary;
import network.foodsharing.api.model.WorkingGroupUpdate;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.GroupService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** Working groups ↔ generated models. */
@Component
public class GroupMapper {

    private final RegionMapper regions;
    private final UserMapper users;

    public GroupMapper(RegionMapper regions, UserMapper users) {
        this.regions = regions;
        this.users = users;
    }

    public WorkingGroupSummary toSummary(GroupService.GroupView view) {
        Region group = view.group();
        WorkingGroupSummary model = new WorkingGroupSummary();
        model.setId(group.getId());
        model.setName(group.getName());
        model.setSlug(group.getSlug());
        model.setParentId(group.getParentId());
        model.setTeaser(group.getTeaser());
        model.setPhotoUrl(group.getPhotoUrl());
        model.setCategory(category(group));
        model.setApplyType(applyType(group));
        model.setFunction(RegionMapper.function(view.function()));
        model.setMemberCount((int) view.memberCount());
        model.setAdminNames(view.admins().stream().map(User::getDisplayName).toList());
        model.setMembership(membership(view));
        model.setMayApply(view.mayApply());
        model.setMayJoin(view.mayJoin());
        model.setMayEdit(view.mayEdit());
        model.setSubGroups(view.subGroups().stream().map(sub -> regions.toGroupRef(sub, null)).toList());
        return model;
    }

    public WorkingGroupDetail toDetail(GroupService.GroupView view) {
        Region group = view.group();
        WorkingGroupDetail model = new WorkingGroupDetail();
        model.setId(group.getId());
        model.setName(group.getName());
        model.setSlug(group.getSlug());
        model.setParent(regions.toRef(view.parent()));
        model.setTeaser(group.getTeaser());
        model.setDescription(group.getDescription());
        model.setPhotoUrl(group.getPhotoUrl());
        model.setCategory(category(group));
        model.setApplyType(applyType(group));
        model.setFunction(RegionMapper.function(view.function()));
        model.setMemberCount((int) view.memberCount());
        model.setAdmins(view.admins().stream().map(users::toSummary).toList());
        model.setMembership(membership(view));
        model.setMayApply(view.mayApply());
        model.setMayJoin(view.mayJoin());
        model.setMayEdit(view.mayEdit());
        GroupRequirements requirements = new GroupRequirements();
        requirements.setMinPickups(group.getMinPickups());
        requirements.setMinBananas(group.getMinBananas());
        requirements.setMinWeeks(group.getMinWeeks());
        model.setRequirements(requirements);
        if (view.requirementCheck() != null) {
            GroupRequirementCheck check = new GroupRequirementCheck();
            check.setPickupsOk(view.requirementCheck().pickupsOk());
            check.setBananasOk(view.requirementCheck().bananasOk());
            check.setWeeksOk(view.requirementCheck().weeksOk());
            check.setPickups(view.requirementCheck().pickups());
            check.setBananas(view.requirementCheck().bananas());
            check.setWeeks(view.requirementCheck().weeks());
            model.setRequirementCheck(check);
        }
        model.setConferenceUrl(view.conferenceUrl());
        model.setEmailName(group.getEmailName());
        model.setSubGroups(view.subGroups().stream().map(sub -> regions.toGroupRef(sub, null)).toList());
        return model;
    }

    public GroupApplication toApplication(GroupService.ApplicationView view) {
        var application = view.application();
        GroupApplication model = new GroupApplication();
        model.setId(application.getId());
        model.setUser(users.toSummary(view.user()));
        model.setText(application.getText());
        model.setStatus(GroupApplicationStatus.fromValue(application.getStatus().name()));
        model.setCreatedAt(TimeMapper.toOffset(application.getCreatedAt()));
        model.setDecidedAt(TimeMapper.toOffset(application.getDecidedAt()));
        model.setDecidedBy(view.decidedBy() == null ? null : users.toSummary(view.decidedBy()));
        model.setDecisionMessage(application.getDecisionMessage());
        return model;
    }

    public GroupMember toMember(GroupService.MemberView view) {
        GroupMember model = new GroupMember();
        model.setUser(users.toSummary(view.user()));
        model.setAdmin(view.admin());
        model.setJoinedAt(TimeMapper.toOffset(view.joinedAt()));
        return model;
    }

    public GroupService.GroupInput toInput(WorkingGroupCreate create) {
        GroupRequirements requirements = create.getRequirements();
        return new GroupService.GroupInput(create.getName(), create.getTeaser(), create.getDescription(),
                create.getPhotoUrl(), category(create.getCategory()), applyType(create.getApplyType()),
                requirements == null ? null : requirements.getMinPickups(),
                requirements == null ? null : requirements.getMinBananas(),
                requirements == null ? null : requirements.getMinWeeks(),
                RegionMapper.function(create.getFunction()), create.getReason());
    }

    public GroupService.GroupInput toInput(WorkingGroupUpdate update) {
        GroupRequirements requirements = update.getRequirements();
        return new GroupService.GroupInput(update.getName(), update.getTeaser(), update.getDescription(),
                update.getPhotoUrl(), category(update.getCategory()), applyType(update.getApplyType()),
                requirements == null ? null : requirements.getMinPickups(),
                requirements == null ? null : requirements.getMinBananas(),
                requirements == null ? null : requirements.getMinWeeks(), null, update.getReason());
    }

    private static GroupMembershipState membership(GroupService.GroupView view) {
        GroupMembershipState state = new GroupMembershipState();
        state.setMember(view.member());
        state.setAdmin(view.admin());
        state.setApplied(view.applied());
        return state;
    }

    private static GroupCategory category(Region group) {
        return GroupCategory.fromValue((group.getGroupCategory() == null
                ? network.foodsharing.region.domain.GroupCategory.PROJECT : group.getGroupCategory()).name());
    }

    private static ApplyType applyType(Region group) {
        return ApplyType.fromValue((group.getApplyType() == null
                ? network.foodsharing.region.domain.ApplyType.NOBODY : group.getApplyType()).name());
    }

    private static network.foodsharing.region.domain.GroupCategory category(GroupCategory category) {
        return category == null ? null : network.foodsharing.region.domain.GroupCategory.valueOf(category.name());
    }

    private static network.foodsharing.region.domain.ApplyType applyType(ApplyType applyType) {
        return applyType == null ? null : network.foodsharing.region.domain.ApplyType.valueOf(applyType.name());
    }
}
