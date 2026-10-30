package network.foodsharing.oauthserver.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.OauthApi;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.ConsentDecision;
import network.foodsharing.api.model.ConsentRedirect;
import network.foodsharing.api.model.ConsentRequest;
import network.foodsharing.api.model.OAuthClientRef;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.oauthserver.service.AuthorizationService;
import network.foodsharing.region.mapper.RegionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The consent page of the OAuth provider (C13, Angular route {@code /oauth/consent?request=}). */
@RestController
public class OAuthConsentController implements OauthApi {

    private final AuthorizationService authorization;
    private final RegionMapper regionMapper;

    public OAuthConsentController(AuthorizationService authorization, RegionMapper regionMapper) {
        this.authorization = authorization;
        this.regionMapper = regionMapper;
    }

    @Override
    public ResponseEntity<ConsentRequest> getConsentRequest(String requestId) {
        AuthorizationService.ConsentView view = authorization.consentRequest(id(requestId));
        return ResponseEntity.ok(new ConsentRequest()
                .client(new OAuthClientRef().clientId(view.client().getClientId()).name(view.client().getName()))
                .scopes(List.copyOf(view.scopes())).alreadyRemembered(view.alreadyRemembered())
                .regionAllowed(view.regionAllowed())
                .requiredRegions(view.requiredRegions().stream().map(regionMapper::toRef).toList())
                .expiresAt(view.request().getExpiresAt().atOffset(java.time.ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<ConsentRedirect> decideConsentRequest(String requestId, ConsentDecision decision) {
        String location = authorization.decide(id(requestId), Boolean.TRUE.equals(decision.getApprove()),
                Boolean.TRUE.equals(decision.getRemember()));
        return ResponseEntity.ok(new ConsentRedirect().redirectUri(location));
    }

    private static UUID id(String requestId) {
        try {
            return UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound(ApiErrorCode.OAUTH_REQUEST_EXPIRED);
        }
    }
}
