package network.foodsharing.auth.service;

import java.util.Objects;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.IdentityProvider;

/**
 * A login identity whose email the provider has verified (Google {@code email_verified}, or a used magic link),
 * plus the sign-up prefill (plan §8.3: only what account creation needs). Optional fields may be null.
 */
public record VerifiedIdentity(IdentityProvider provider, String subject, String email, String label,
                               String displayName, String firstName, String lastName, String avatarUrl,
                               Language language) {

    public VerifiedIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(email, "email");
    }

    /** A magic-link identity: subject = label = the normalized email. */
    public static VerifiedIdentity email(String normalizedEmail, Language language) {
        return new VerifiedIdentity(IdentityProvider.EMAIL, normalizedEmail, normalizedEmail, normalizedEmail, null,
                null, null, null, language);
    }
}
