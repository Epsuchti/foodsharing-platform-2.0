package network.foodsharing.dev.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The seed users of plan §7.3 in journey order (ids: docs/seed-ids.md, data: db/seed/user/user_account.csv). The dev
 * user switcher lists them in this order; the fake OIDC provider offers the ones with a Google subject.
 */
@Component
@Profile({"local", "e2e"})
public class DevUserCatalog {

    /** Prefix of the Google subjects of seed identities ({@code fake-google-<name>}). */
    public static final String GOOGLE_SUBJECT_PREFIX = "fake-google-";

    private static final List<Entry> ENTRIES = List.of(
            entry(1, "newbie", false),
            entry(2, "profile", false),
            entry(3, "quiz", false),
            entry(4, "einab1", false),
            entry(5, "pending", false),
            entry(6, "foodsaver", true),
            entry(7, "jumper", false),
            entry(8, "sleepy", false),
            entry(9, "bieb", false),
            entry(10, "guide", false),
            entry(11, "bot", true),
            entry(12, "welcome", false),
            entry(13, "reports", false),
            entry(14, "orga", false));

    /** One seed user. {@code googleSubject} is {@code null} when the user has no Google identity. */
    public record Entry(String name, UUID id, String googleSubject) {

        public String email() {
            return name + "@local";
        }

        /** i18n key of the switcher's one-line description of the journey stage. */
        public String descriptionKey() {
            return "dev.users." + name;
        }
    }

    public List<Entry> entries() {
        return ENTRIES;
    }

    public Optional<Entry> byId(UUID id) {
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public Optional<Entry> byGoogleSubject(String subject) {
        return ENTRIES.stream().filter(entry -> subject != null && subject.equals(entry.googleSubject())).findFirst();
    }

    /** Seed user id convention {@code 00000001-0001-4000-8000-<sequence>} (docs/conventions.md A6). */
    public static UUID seedUserId(int sequence) {
        return UUID.fromString(String.format("00000001-0001-4000-8000-%012d", sequence));
    }

    private static Entry entry(int sequence, String name, boolean google) {
        return new Entry(name, seedUserId(sequence), google ? GOOGLE_SUBJECT_PREFIX + name : null);
    }
}
