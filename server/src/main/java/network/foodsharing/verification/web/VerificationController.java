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
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verification API (implemented in T16).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class VerificationController implements VerificationApi {

    @Override
    public ResponseEntity<IdentityStatus> confirmIdentity(UUID userId) {
        throw ApiException.notImplemented("confirmIdentity");
    }

    @Override
    public ResponseEntity<IdentityStatus> getIdentityStatus(UUID userId) {
        throw ApiException.notImplemented("getIdentityStatus");
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getVerificationHistory(UUID userId, List<String> actions, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        throw ApiException.notImplemented("getVerificationHistory");
    }

    @Override
    public ResponseEntity<VerificationCandidatePage> listVerificationQueue(UUID regionId, VerificationQueueFilter filter, String search, Integer page, Integer size) {
        throw ApiException.notImplemented("listVerificationQueue");
    }

    @Override
    public ResponseEntity<IdentityStatus> revertIdentityConfirmation(UUID userId, ReasonBody reasonBody) {
        throw ApiException.notImplemented("revertIdentityConfirmation");
    }

    @Override
    public ResponseEntity<UserSummary> unverifyUser(UUID userId, RequiredMessage requiredMessage) {
        throw ApiException.notImplemented("unverifyUser");
    }

    @Override
    public ResponseEntity<UserSummary> verifyUser(UUID userId, RequiredMessage requiredMessage) {
        throw ApiException.notImplemented("verifyUser");
    }
}
