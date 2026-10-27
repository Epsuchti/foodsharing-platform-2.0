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
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Working-group API (implemented in T12).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class WorkingGroupsController implements GroupsApi {

    @Override
    public ResponseEntity<GroupMember> addGroupMember(UUID groupId, UUID userId) {
        throw ApiException.notImplemented("addGroupMember");
    }

    @Override
    public ResponseEntity<GroupApplication> applyToGroup(UUID groupId, GroupApplicationCreate groupApplicationCreate) {
        throw ApiException.notImplemented("applyToGroup");
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> archiveGroup(UUID groupId, OptionalReasonBody optionalReasonBody) {
        throw ApiException.notImplemented("archiveGroup");
    }

    @Override
    public ResponseEntity<Void> cancelMyApplication(UUID groupId) {
        throw ApiException.notImplemented("cancelMyApplication");
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> createGroup(UUID regionId, WorkingGroupCreate workingGroupCreate) {
        throw ApiException.notImplemented("createGroup");
    }

    @Override
    public ResponseEntity<GroupApplication> decideGroupApplication(UUID groupId, UUID applicationId, GroupApplicationDecision groupApplicationDecision) {
        throw ApiException.notImplemented("decideGroupApplication");
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> getGroup(UUID groupId) {
        throw ApiException.notImplemented("getGroup");
    }

    @Override
    public ResponseEntity<ConferenceLink> getGroupConference(UUID groupId) {
        throw ApiException.notImplemented("getGroupConference");
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> joinOpenGroup(UUID groupId) {
        throw ApiException.notImplemented("joinOpenGroup");
    }

    @Override
    public ResponseEntity<List<GroupApplication>> listGroupApplications(UUID groupId, GroupApplicationStatus status) {
        throw ApiException.notImplemented("listGroupApplications");
    }

    @Override
    public ResponseEntity<List<GroupMember>> listGroupMembers(UUID groupId) {
        throw ApiException.notImplemented("listGroupMembers");
    }

    @Override
    public ResponseEntity<List<WorkingGroupSummary>> listGroupsInRegion(UUID regionId, Boolean includeArchived) {
        throw ApiException.notImplemented("listGroupsInRegion");
    }

    @Override
    public ResponseEntity<List<WorkingGroupSummary>> listMyGroups() {
        throw ApiException.notImplemented("listMyGroups");
    }

    @Override
    public ResponseEntity<Void> removeGroupAdmin(UUID groupId, UUID userId) {
        throw ApiException.notImplemented("removeGroupAdmin");
    }

    @Override
    public ResponseEntity<Void> removeGroupMember(UUID groupId, UUID userId, ReasonBody reasonBody) {
        throw ApiException.notImplemented("removeGroupMember");
    }

    @Override
    public ResponseEntity<MessageResponse> sendGroupMail(UUID groupId, GroupMail groupMail) {
        throw ApiException.notImplemented("sendGroupMail");
    }

    @Override
    public ResponseEntity<GroupMember> setGroupAdmin(UUID groupId, UUID userId) {
        throw ApiException.notImplemented("setGroupAdmin");
    }

    @Override
    public ResponseEntity<WorkingGroupDetail> updateGroup(UUID groupId, WorkingGroupUpdate workingGroupUpdate) {
        throw ApiException.notImplemented("updateGroup");
    }
}
