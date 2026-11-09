package network.foodsharing.dev.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.seed.SeedPersonas;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The seed users of plan §7.3 in journey order for the dev user switcher and the fake OIDC provider (the ones with a
 * Google subject). The list itself lives in the profile-free {@link SeedPersonas}, shared with the staging persona
 * panel (decision 173).
 */
@Component
@Profile({"local", "e2e"})
public class DevUserCatalog {

    /** Prefix of the Google subjects of seed identities ({@code fake-google-<name>}). */
    public static final String GOOGLE_SUBJECT_PREFIX = SeedPersonas.GOOGLE_SUBJECT_PREFIX;

    public List<SeedPersonas.Entry> entries() {
        return SeedPersonas.entries();
    }

    public Optional<SeedPersonas.Entry> byId(UUID id) {
        return SeedPersonas.byId(id);
    }

    public Optional<SeedPersonas.Entry> byGoogleSubject(String subject) {
        return SeedPersonas.byGoogleSubject(subject);
    }

    /** Seed user id convention {@code 00000001-0001-4000-8000-<sequence>} (docs/conventions.md A6). */
    public static UUID seedUserId(int sequence) {
        return SeedPersonas.seedUserId(sequence);
    }
}
