package network.foodsharing.region.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.GroupsApi;
import network.foodsharing.api.model.ConferenceLink;
import network.foodsharing.api.model.GroupApplication;
import network.foodsharing.api.model.GroupApplicationCreate;
import network.foodsharing.api.model.GroupApplicationDecision;
import network.foodsharing.api.model.GroupApplicationStatus;
import network.foodsharing.api.model.GroupMail;
import network.foodsharing.api.model.GroupMember;
import network.foodsharing.api.model.MessageResponse;
import network.foodsharing.api.model.OptionalReasonBody;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.WorkingGroupCreate;
import network.foodsharing.api.model.WorkingGroupDetail;
import network.foodsharing.api.model.WorkingGroupSummary;
import network.foodsharing.api.model.WorkingGroupUpdate;
import network.foodsharing.region.mapper.GroupMapper;
import network.foodsharing.region.service.GroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Working-group API (plan §13.2 R9–R12). */
@RestController
public class WorkingGroupsController implements GroupsApi {

    private final GroupService groups;
    private final GroupMapper mapper;

    public WorkingGroupsController(GroupService groups, GroupMapper mapper) {
        this.groups = groups;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<GroupMember> addGroupMember(UUID groupId, UUID userId) {
        return ResponseEntity.ok(mapper.toMember(groups.addMember(groupId, userId)));
    }

    @Override
    public ResponseEntity<GroupApplication> applyToGroup(UUID groupId, GroupApplicationCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toApplication(groups.apply(groupId, body.getText())));
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> archiveGroup(UUID groupId, OptionalReasonBody body) {
        return ResponseEntity.ok(mapper.toDetail(groups.archive(groupId, body == null ? null : body.getReason())));
    }

    @Override
    public ResponseEntity<Void> cancelMyApplication(UUID groupId) {
        groups.cancelMine(groupId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> createGroup(UUID regionId, WorkingGroupCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDetail(groups.create(regionId, mapper.toInput(body))));
    }

    @Override
    public ResponseEntity<GroupApplication> decideGroupApplication(UUID groupId, UUID applicationId,
                                                                   GroupApplicationDecision body) {
        return ResponseEntity.ok(mapper.toApplication(groups.decide(groupId, applicationId,
                Boolean.TRUE.equals(body.getAccept()), body.getMessage())));
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> getGroup(UUID groupId) {
        return ResponseEntity.ok(mapper.toDetail(groups.detail(groupId)));
    }

    @Override
    public ResponseEntity<ConferenceLink> getGroupConference(UUID groupId) {
        ConferenceLink link = new ConferenceLink();
        link.setUrl(groups.conferenceUrl(groupId));
        return ResponseEntity.ok(link);
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> joinOpenGroup(UUID groupId) {
        return ResponseEntity.ok(mapper.toDetail(groups.joinOpen(groupId)));
    }

    @Override
    public ResponseEntity<List<GroupApplication>> listGroupApplications(UUID groupId, GroupApplicationStatus status) {
        return ResponseEntity.ok(groups.applications(groupId, status == null ? null
                        : network.foodsharing.region.domain.GroupApplicationStatus.valueOf(status.name())).stream()
                .map(mapper::toApplication).toList());
    }

    @Override
    public ResponseEntity<List<GroupMember>> listGroupMembers(UUID groupId) {
        return ResponseEntity.ok(groups.members(groupId).stream().map(mapper::toMember).toList());
    }

    @Override
    public ResponseEntity<List<WorkingGroupSummary>> listGroupsInRegion(UUID regionId, Boolean includeArchived) {
        return ResponseEntity.ok(groups.groupsIn(regionId, Boolean.TRUE.equals(includeArchived)).stream()
                .map(mapper::toSummary).toList());
    }

    @Override
    public ResponseEntity<List<WorkingGroupSummary>> listMyGroups() {
        return ResponseEntity.ok(groups.myGroups().stream().map(mapper::toSummary).toList());
    }

    @Override
    public ResponseEntity<Void> removeGroupAdmin(UUID groupId, UUID userId) {
        groups.removeAdmin(groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeGroupMember(UUID groupId, UUID userId, ReasonBody body) {
        groups.removeMember(groupId, userId, body.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessageResponse> sendGroupMail(UUID groupId, GroupMail body) {
        int recipients = groups.sendMail(groupId, body.getSubject(), body.getBody());
        MessageResponse response = new MessageResponse();
        response.setMessage("Mail queued for " + recipients + " members");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Override
    public ResponseEntity<GroupMember> setGroupAdmin(UUID groupId, UUID userId) {
        return ResponseEntity.ok(mapper.toMember(groups.setAdmin(groupId, userId)));
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> updateGroup(UUID groupId, WorkingGroupUpdate body) {
        return ResponseEntity.ok(mapper.toDetail(groups.update(groupId, mapper.toInput(body))));
    }
}
