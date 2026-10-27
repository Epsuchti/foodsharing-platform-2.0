package network.foodsharing.content.web;

import network.foodsharing.api.PublicLegalApi;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.LegalDocument;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public legal documents API (implemented in T14).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class PublicLegalController implements PublicLegalApi {

    @Override
    public ResponseEntity<LegalDocument> getLegalDocument(LegalDocumentKind kind, Language lang, String version) {
        throw ApiException.notImplemented("getLegalDocument");
    }
}
