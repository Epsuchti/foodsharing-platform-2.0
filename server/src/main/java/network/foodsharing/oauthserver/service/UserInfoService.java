package network.foodsharing.oauthserver.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import network.foodsharing.config.AppProperties;
import network.foodsharing.oauthserver.domain.OAuthScope;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OIDC userinfo (upstream docs/en/oauth.md): {@code sub}, {@code preferred_username} (display name + foodsaver number);
 * {@code profile} adds name, given/family name, picture and locale; {@code email} adds email and email_verified;
 * {@code regions} adds {@code region_ids} (active memberships) and {@code region_ambassador} (direct admin).
 */
@Service
public class UserInfoService {

    private final TokenService tokens;
    private final UserService users;
    private final RegionService regions;
    private final AppProperties properties;

    public UserInfoService(TokenService tokens, UserService users, RegionService regions, AppProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.regions = regions;
        this.properties = properties;
    }

    /** @throws OAuthProtocolException invalid_token (401) */
    @Transactional(readOnly = true)
    public Map<String, Object> userInfo(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw OAuthProtocolException.invalidToken("Bearer token required");
        }
        TokenService.AccessClaims access = tokens.verifyAccessToken(authorizationHeader.substring(7).trim());
        User user = users.findActive(access.userId())
                .orElseThrow(() -> OAuthProtocolException.invalidToken("account not active"));
        return claims(user, access.scopes(), regions.activeRegionIdsOf(user.getId()),
                regions.adminRegionIds(user.getId()), properties.getBaseUrl());
    }

    /** The claims of one user for the granted scopes (pure, unit-tested). */
    public static Map<String, Object> claims(User user, Set<String> scopes, Set<java.util.UUID> memberRegionIds,
                                             Set<java.util.UUID> adminRegionIds, String baseUrl) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("preferred_username", user.getDisplayName() + "#" + user.getFoodsaverNumber());
        if (scopes.contains(OAuthScope.profile.name())) {
            String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                    + (user.getLastName() == null ? "" : user.getLastName())).trim();
            claims.put("name", name.isEmpty() ? user.getDisplayName() : name);
            claims.put("given_name", user.getFirstName());
            claims.put("family_name", user.getLastName());
            String picture = picture(user, baseUrl);
            if (picture != null) {
                claims.put("picture", picture);
            }
            claims.put("locale", locale(user.getPreferredLanguage() == null ? "de"
                    : user.getPreferredLanguage().getValue()));
        }
        if (scopes.contains(OAuthScope.email.name())) {
            claims.put("email", user.getEmail());
            claims.put("email_verified", true);
        }
        if (scopes.contains(OAuthScope.regions.name())) {
            claims.put("region_ids", memberRegionIds.stream().map(String::valueOf).sorted().toList());
            claims.put("region_ambassador", adminRegionIds.stream().map(String::valueOf).sorted().toList());
        }
        return claims;
    }

    static String locale(String language) {
        return switch (language) {
            case "en" -> "en";
            case "gws" -> "gsw-CH";
            case "fr" -> "fr-CH";
            case "it" -> "it-CH";
            default -> "de-CH";
        };
    }

    private static String picture(User user, String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (user.getPhotoUploadId() != null) {
            return base + "/api/uploads/" + user.getPhotoUploadId() + "?size=md";
        }
        String avatar = user.getAvatarUrl();
        if (avatar == null || avatar.isBlank()) {
            return null;
        }
        return avatar.startsWith("/") ? base + avatar : avatar;
    }
}
