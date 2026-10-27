package network.foodsharing.content.web;

import java.util.List;
import network.foodsharing.api.LegalApi;
import network.foodsharing.api.model.ConsentStatus;
import network.foodsharing.api.model.LegalConsentRequest;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.api.model.LegalVersionCreate;
import network.foodsharing.api.model.LegalVersionView;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legal consent API (implemented in T14).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class LegalController implements LegalApi {

    @Override
    public ResponseEntity<ConsentStatus> acceptLegalDocument(LegalConsentRequest legalConsentRequest) {
        throw ApiException.notImplemented("acceptLegalDocument");
    }

    @Override
    public ResponseEntity<List<ConsentStatus>> getMyConsents() {
        throw ApiException.notImplemented("getMyConsents");
    }

    @Override
    public ResponseEntity<List<LegalVersionView>> listLegalVersions(LegalDocumentKind kind) {
        throw ApiException.notImplemented("listLegalVersions");
    }

    @Override
    public ResponseEntity<LegalVersionView> publishLegalVersion(LegalDocumentKind kind, LegalVersionCreate legalVersionCreate) {
        throw ApiException.notImplemented("publishLegalVersion");
    }
}
