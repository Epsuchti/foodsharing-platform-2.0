package network.foodsharing.verification.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PassportsApi;
import network.foodsharing.api.model.CreateRegionPassports;
import network.foodsharing.api.model.GoogleWalletLink;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.JobRef;
import network.foodsharing.api.model.PassportRequestView;
import network.foodsharing.api.model.PassportStatus;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passport API (implemented in T16).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class PassportsController implements PassportsApi {

    @Override
    public ResponseEntity<JobRef> createRegionPassports(UUID regionId, CreateRegionPassports createRegionPassports) {
        throw ApiException.notImplemented("createRegionPassports");
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> downloadMyPassport() {
        throw ApiException.notImplemented("downloadMyPassport");
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getAppleWalletPass() {
        throw ApiException.notImplemented("getAppleWalletPass");
    }

    @Override
    public ResponseEntity<GoogleWalletLink> getGoogleWalletLink() {
        throw ApiException.notImplemented("getGoogleWalletLink");
    }

    @Override
    public ResponseEntity<PassportStatus> getMyPassportStatus() {
        throw ApiException.notImplemented("getMyPassportStatus");
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getPassportHistory(UUID userId, List<String> actions, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        throw ApiException.notImplemented("getPassportHistory");
    }

    @Override
    public ResponseEntity<List<PassportRequestView>> listPassportRequests(UUID regionId, Boolean includeFulfilled) {
        throw ApiException.notImplemented("listPassportRequests");
    }

    @Override
    public ResponseEntity<PassportStatus> requestPassport() {
        throw ApiException.notImplemented("requestPassport");
    }
}
