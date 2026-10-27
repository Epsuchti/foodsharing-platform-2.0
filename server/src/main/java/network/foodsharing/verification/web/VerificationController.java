package network.foodsharing.verification.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.VerificationApi;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.IdentityStatus;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.RequiredMessage;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.api.model.VerificationCandidatePage;
import network.foodsharing.api.model.VerificationQueueFilter;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import network.foodsharing.verification.mapper.VerificationMapper;
import network.foodsharing.verification.service.VerificationQueueService;
import network.foodsharing.verification.service.VerificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Verification queue, verify / unverify, identity confirmation and verification history (plan §13.9 Ve2). */
@RestController
public class VerificationController implements VerificationApi {

    static final List<String> VERIFICATION_ACTIONS = List.of("VERIFIED", "UNVERIFIED", "IDENTITY_CONFIRMED",
            "IDENTITY_CONFIRMATION_REVERTED");

    private final VerificationService verification;
    private final VerificationQueueService queue;
    private final VerificationMapper mapper;
    private final UserMapper userMapper;
    private final UserService users;
    private final HistoryQueryService historyQuery;
    private final HistoryMapper historyMapper;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;

    public VerificationController(VerificationService verification, VerificationQueueService queue,
                                  VerificationMapper mapper, UserMapper userMapper, UserService users,
                                  HistoryQueryService historyQuery, HistoryMapper historyMapper,
                                  CurrentActorService currentActor, PermissionService permissions) {
        this.verification = verification;
        this.queue = queue;
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.users = users;
        this.historyQuery = historyQuery;
        this.historyMapper = historyMapper;
        this.currentActor = currentActor;
        this.permissions = permissions;
    }

    @Override
    public ResponseEntity<IdentityStatus> confirmIdentity(UUID userId) {
        return ResponseEntity.ok(mapper.toIdentity(verification.confirmIdentity(userId)));
    }

    @Override
    public ResponseEntity<IdentityStatus> getIdentityStatus(UUID userId) {
        return ResponseEntity.ok(mapper.toIdentity(verification.identity(userId)));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getVerificationHistory(UUID userId, List<String> actions,
                                                                   OffsetDateTime from, OffsetDateTime to,
                                                                   Integer page, Integer size) {
        users.require(userId);
        permissions.requireMayAdministrateUser(currentActor.requirePrincipal(), userId);
        HistoryFilter filter = new HistoryFilter(SubjectType.USER, userId, null, null,
                actions == null || actions.isEmpty() ? VERIFICATION_ACTIONS
                        : actions.stream().filter(VERIFICATION_ACTIONS::contains).toList(),
                TimeMapper.toInstant(from), TimeMapper.toInstant(to), page == null ? 0 : page,
                size == null ? 20 : size);
        return ResponseEntity.ok(historyMapper.toPage(historyQuery.list(filter)));
    }

    @Override
    public ResponseEntity<VerificationCandidatePage> listVerificationQueue(UUID regionId,
                                                                           VerificationQueueFilter filter,
                                                                           String search, Integer page,
                                                                           Integer size) {
        Page<VerificationQueueService.Candidate> result = queue.queue(regionId,
                filter == null ? VerificationQueueService.Filter.READY
                        : VerificationQueueService.Filter.valueOf(filter.name()), search, page, size);
        VerificationCandidatePage model = new VerificationCandidatePage();
        model.setItems(result.getContent().stream().map(mapper::toCandidate).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<IdentityStatus> revertIdentityConfirmation(UUID userId, ReasonBody body) {
        return ResponseEntity.ok(mapper.toIdentity(verification.revertIdentity(userId,
                body == null ? null : body.getReason())));
    }

    @Override
    public ResponseEntity<UserSummary> unverifyUser(UUID userId, RequiredMessage body) {
        return ResponseEntity.ok(userMapper.toSummary(verification.unverify(userId,
                body == null ? null : body.getMessage())));
    }

    @Override
    public ResponseEntity<UserSummary> verifyUser(UUID userId, RequiredMessage body) {
        return ResponseEntity.ok(userMapper.toSummary(verification.verify(userId,
                body == null ? null : body.getMessage())));
    }
}
