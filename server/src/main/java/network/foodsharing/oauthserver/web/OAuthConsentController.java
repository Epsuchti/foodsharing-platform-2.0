package network.foodsharing.oauthserver.web;

import network.foodsharing.api.OauthApi;
import network.foodsharing.api.model.ConsentDecision;
import network.foodsharing.api.model.ConsentRedirect;
import network.foodsharing.api.model.ConsentRequest;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class OAuthConsentController implements OauthApi {

    @Override
    public ResponseEntity<ConsentRedirect> decideConsentRequest(String requestId, ConsentDecision consentDecision) {
        throw ApiException.notImplemented("decideConsentRequest");
    }

    @Override
    public ResponseEntity<ConsentRequest> getConsentRequest(String requestId) {
        throw ApiException.notImplemented("getConsentRequest");
    }
}
