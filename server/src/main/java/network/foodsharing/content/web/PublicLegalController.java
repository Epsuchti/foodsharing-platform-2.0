package network.foodsharing.content.web;

import network.foodsharing.api.PublicLegalApi;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.LegalDocument;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.content.service.LegalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Published legal documents (no login). */
@RestController
public class PublicLegalController implements PublicLegalApi {

    private final LegalService legal;

    public PublicLegalController(LegalService legal) {
        this.legal = legal;
    }

    @Override
    public ResponseEntity<LegalDocument> getLegalDocument(LegalDocumentKind kind, Language lang, String version) {
        return ResponseEntity.ok(ContentModels.document(legal.document(ContentModels.kind(kind),
                ContentModels.language(lang), version)));
    }
}
