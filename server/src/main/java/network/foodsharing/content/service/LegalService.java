package network.foodsharing.content.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.ContentAction;
import network.foodsharing.content.domain.LegalAcceptance;
import network.foodsharing.content.domain.LegalAction;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.content.domain.LegalDocumentVersion;
import network.foodsharing.content.domain.LegalDocumentVersionI18n;
import network.foodsharing.content.repository.LegalAcceptanceRepository;
import network.foodsharing.content.repository.LegalDocumentVersionI18nRepository;
import network.foodsharing.content.repository.LegalDocumentVersionRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned legal documents and consents (plan §13.9 Ve1, §14.1 EA4).
 * <ul>
 *   <li>The current version of a kind is the latest one published at or before now.</li>
 *   <li>A user's consent is current when they accepted the current version, or an older version and no later version
 *       required everybody to consent again.</li>
 *   <li>Required: PRIVACY_POLICY for everyone, LEGAL_AGREEMENT for foodsavers (role or quiz role ≥ FOODSAVER; journey
 *       step 5), CONFIDENTIALITY_NOTICE for store managers. Pending = required and not current.</li>
 *   <li>Only the current version can be accepted (409 LEGAL_VERSION_OUTDATED).</li>
 * </ul>
 */
@Service
public class LegalService {

    public record Document(LegalDocumentVersion version, String summary, String body, String language,
                           boolean fallback) {
    }

    public record Consent(LegalDocumentKind kind, String currentVersion, String acceptedVersion, Instant acceptedAt,
                          boolean required, boolean pending) {
    }

    public record VersionInput(String version, Map<String, String> summaries, Map<String, String> bodies,
                               boolean requireReconsent) {
    }

    private final LegalDocumentVersionRepository versions;
    private final LegalDocumentVersionI18nRepository versionTexts;
    private final LegalAcceptanceRepository acceptances;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final Clock clock;

    public LegalService(LegalDocumentVersionRepository versions, LegalDocumentVersionI18nRepository versionTexts,
                        LegalAcceptanceRepository acceptances, UserService users, CurrentActorService currentActor,
                        PermissionService permissions, HistoryService history, Clock clock) {
        this.versions = versions;
        this.versionTexts = versionTexts;
        this.acceptances = acceptances;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<LegalDocumentVersion> currentVersion(LegalDocumentKind kind) {
        Instant now = clock.instant();
        return versions.findByKindOrderByPublishedAtDesc(kind).stream()
                .filter(version -> !version.getPublishedAt().isAfter(now)).findFirst();
    }

    /** The current (or a given) version in a language. @throws ApiException 404 LEGAL_DOCUMENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public Document document(LegalDocumentKind kind, String language, String version) {
        LegalDocumentVersion row = (version == null ? currentVersion(kind) : versions.findByKindAndDocumentVersion(kind, version))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
        Map<String, LegalDocumentVersionI18n> texts = versionTexts.findByOwnerId(row.getId()).stream()
                .collect(Collectors.toMap(LegalDocumentVersionI18n::getLanguage, Function.identity()));
        LegalDocumentVersionI18n text = ContentService.pick(texts, language);
        if (text == null) {
            throw ApiException.notFound(ApiErrorCode.LEGAL_DOCUMENT_NOT_FOUND);
        }
        return new Document(row, text.getSummary(), text.getBody(), text.getLanguage(),
                !text.getLanguage().equals(language));
    }

    @Transactional(readOnly = true)
    public List<Consent> myConsents() {
        return consents(users.requireActive(currentActor.requireUserId()));
    }

    @Transactional(readOnly = true)
    public List<Consent> consents(User user) {
        List<LegalAcceptance> accepted = acceptances.findByUserIdOrderByAcceptedAtDesc(user.getId());
        List<Consent> result = new ArrayList<>();
        for (LegalDocumentKind kind : LegalDocumentKind.values()) {
            Optional<LegalDocumentVersion> current = currentVersion(kind);
            Optional<LegalAcceptance> latest = accepted.stream().filter(a -> a.getKind() == kind).findFirst();
            boolean required = required(user, kind) && current.isPresent();
            boolean upToDate = current.isPresent() && latest.isPresent() && isCurrent(kind, latest.get(), current.get());
            result.add(new Consent(kind, current.map(LegalDocumentVersion::getDocumentVersion).orElse(null),
                    latest.map(LegalAcceptance::getDocumentVersion).orElse(null),
                    latest.map(LegalAcceptance::getAcceptedAt).orElse(null), required, required && !upToDate));
        }
        return result;
    }

    /** Kinds the user still has to accept (SessionInfo.pendingConsents). */
    @Transactional(readOnly = true)
    public List<LegalDocumentKind> pendingConsents(UUID userId) {
        return users.findActive(userId).map(user -> consents(user).stream().filter(Consent::pending)
                .map(Consent::kind).toList()).orElse(List.of());
    }

    /** Does the user hold a current consent for the kind (whether required or not)? */
    @Transactional(readOnly = true)
    public boolean hasCurrentConsent(UUID userId, LegalDocumentKind kind) {
        Optional<LegalDocumentVersion> current = currentVersion(kind);
        if (current.isEmpty()) {
            return false;
        }
        return acceptances.findByUserIdOrderByAcceptedAtDesc(userId).stream().filter(a -> a.getKind() == kind)
                .findFirst().map(latest -> isCurrent(kind, latest, current.get())).orElse(false);
    }

    /**
     * Accepts the current version of a document; records LegalAction.CONSENT_GIVEN.
     *
     * @throws ApiException 404 LEGAL_DOCUMENT_NOT_FOUND, 409 LEGAL_VERSION_OUTDATED
     */
    @Transactional
    public Consent accept(LegalDocumentKind kind, String version) {
        User user = users.requireActive(currentActor.requireUserId());
        LegalDocumentVersion requested = versions.findByKindAndDocumentVersion(kind, version)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
        LegalDocumentVersion current = currentVersion(kind).orElseThrow(
                () -> ApiException.notFound(ApiErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
        if (!current.getId().equals(requested.getId())) {
            throw ApiException.conflict(ApiErrorCode.LEGAL_VERSION_OUTDATED).withArg("current", current.getDocumentVersion());
        }
        if (acceptances.findByUserIdAndKindAndDocumentVersion(user.getId(), kind, version).isEmpty()) {
            LegalAcceptance acceptance = new LegalAcceptance();
            acceptance.setUserId(user.getId());
            acceptance.setKind(kind);
            acceptance.setDocumentVersion(version);
            acceptance.setAcceptedAt(clock.instant());
            acceptances.saveAndFlush(acceptance);
            history.record(history.event(LegalAction.CONSENT_GIVEN, user.getId())
                    .metadata(Map.of("kind", kind.name(), "version", version)).build());
        }
        return consents(user).stream().filter(c -> c.kind() == kind).findFirst().orElseThrow();
    }

    /** @throws ApiException 403 unless ORGA */
    @Transactional(readOnly = true)
    public List<LegalDocumentVersion> versions(LegalDocumentKind kind) {
        permissions.requireOrga(currentActor.requirePrincipal());
        return versions.findByKindOrderByPublishedAtDesc(kind);
    }

    /**
     * Publishes a new version (now). With {@code requireReconsent} everybody who has to consent is asked again.
     *
     * @throws ApiException 403 unless ORGA, 409 LEGAL_VERSION_EXISTS, 400 VALIDATION_FAILED
     */
    @Transactional
    public LegalDocumentVersion publish(LegalDocumentKind kind, VersionInput input) {
        permissions.requireOrga(currentActor.requirePrincipal());
        if (versions.existsByKindAndDocumentVersion(kind, input.version())) {
            throw ApiException.conflict(ApiErrorCode.LEGAL_VERSION_EXISTS);
        }
        if (input.bodies() == null || input.bodies().isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "bodies");
        }
        LegalDocumentVersion version = new LegalDocumentVersion();
        version.setKind(kind);
        version.setDocumentVersion(input.version());
        version.setPublishedAt(clock.instant());
        version.setRequireReconsent(input.requireReconsent());
        LegalDocumentVersion saved = versions.saveAndFlush(version);
        input.bodies().forEach((language, body) -> {
            LegalDocumentVersionI18n text = new LegalDocumentVersionI18n();
            text.setOwnerId(saved.getId());
            text.setLanguage(language);
            text.setBody(body);
            text.setSummary(input.summaries() == null ? null : input.summaries().get(language));
            versionTexts.save(text);
        });
        history.record(history.event(ContentAction.LEGAL_VERSION_PUBLISHED, saved.getId())
                .metadata(Map.of("kind", kind.name(), "version", input.version(),
                        "requireReconsent", input.requireReconsent())).build());
        return saved;
    }

    private boolean required(User user, LegalDocumentKind kind) {
        return switch (kind) {
            case PRIVACY_POLICY -> true;
            case LEGAL_AGREEMENT -> PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)
                    || PermissionService.isAtLeast(user.getQuizRole(), Role.FOODSAVER);
            case CONFIDENTIALITY_NOTICE -> PermissionService.isAtLeast(user.getRole(), Role.STORE_MANAGER);
        };
    }

    /** No version requiring a new consent was published after the accepted one. */
    private boolean isCurrent(LegalDocumentKind kind, LegalAcceptance latest, LegalDocumentVersion current) {
        if (latest.getDocumentVersion().equals(current.getDocumentVersion())) {
            return true;
        }
        Instant acceptedVersionPublished = versions.findByKindAndDocumentVersion(kind, latest.getDocumentVersion())
                .map(LegalDocumentVersion::getPublishedAt).orElse(Instant.EPOCH);
        Instant now = clock.instant();
        return versions.findByKindOrderByPublishedAtDesc(kind).stream()
                .filter(v -> v.getPublishedAt().isAfter(acceptedVersionPublished) && !v.getPublishedAt().isAfter(now))
                .noneMatch(LegalDocumentVersion::isRequireReconsent);
    }
}
