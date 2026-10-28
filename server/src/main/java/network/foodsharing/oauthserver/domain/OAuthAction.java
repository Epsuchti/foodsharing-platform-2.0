package network.foodsharing.oauthserver.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** OAuth provider history (subject OAUTH_CLIENT). Never carries token or secret values. */
public enum OAuthAction implements HistoryAction {
    CLIENT_CREATED,
    CLIENT_UPDATED,
    CLIENT_DELETED,
    SECRET_ROTATED,
    CONSENT_GRANTED,
    CONSENT_REVOKED,
    TOKEN_ISSUED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.OAUTH_CLIENT;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
