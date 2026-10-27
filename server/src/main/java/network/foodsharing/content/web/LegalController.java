package network.foodsharing.content.web;

import java.util.List;
import network.foodsharing.api.LegalApi;
import network.foodsharing.api.model.ConsentStatus;
import network.foodsharing.api.model.LegalConsentRequest;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.api.model.LegalVersionCreate;
import network.foodsharing.api.model.LegalVersionView;
import network.foodsharing.content.service.LegalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Consents and publishing legal versions (plan §13.9 Ve1). */
@RestController
public class LegalController implements LegalApi {

    private final LegalService legal;

    public LegalController(LegalService legal) {
        this.legal = legal;
    }

    @Override
    public ResponseEntity<ConsentStatus> acceptLegalDocument(LegalConsentRequest body) {
        return ResponseEntity.ok(ContentModels.consent(legal.accept(ContentModels.kind(body.getKind()),
                body.getVersion())));
    }

    @Override
    public ResponseEntity<List<ConsentStatus>> getMyConsents() {
        return ResponseEntity.ok(legal.myConsents().stream().map(ContentModels::consent).toList());
    }

    @Override
    public ResponseEntity<List<LegalVersionView>> listLegalVersions(LegalDocumentKind kind) {
        return ResponseEntity.ok(legal.versions(ContentModels.kind(kind)).stream().map(ContentModels::version).toList());
    }

    @Override
    public ResponseEntity<LegalVersionView> publishLegalVersion(LegalDocumentKind kind, LegalVersionCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ContentModels.version(legal.publish(
                ContentModels.kind(kind), new LegalService.VersionInput(body.getVersion(), body.getSummaries(),
                        body.getBodies(), Boolean.TRUE.equals(body.getRequireReconsent())))));
    }
}
