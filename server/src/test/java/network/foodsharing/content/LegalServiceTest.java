package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.content.service.LegalService.Consent;
import network.foodsharing.content.service.LegalService.VersionInput;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Consent rules (plan §13.9 Ve1): required kinds, pending, re-consent, outdated versions; content fallback. */
@JpaSliceTest
@Import({LegalService.class, ContentService.class, UserService.class, RegionService.class,
        RegionClosureRepository.class})
class LegalServiceTest {

    @Autowired
    private LegalService legal;

    @Autowired
    private ContentService content;

    @Autowired
    private UserRepository users;

    @Autowired
    private MutableClock clock;

    private User orga;
    private User foodsharer;
    private User foodsaver;

    @BeforeEach
    void setUp() {
        orga = users.saveAndFlush(TestUsers.user("orga@test.local", Role.ORGA));
        foodsharer = users.saveAndFlush(TestUsers.user("sharer@test.local", Role.FOODSHARER));
        foodsaver = users.saveAndFlush(TestUsers.user("saver@test.local", Role.FOODSAVER));
        TestAuth.login(TestUsers.principal(orga));
        legal.publish(LegalDocumentKind.PRIVACY_POLICY, input("2026-01", false));
        legal.publish(LegalDocumentKind.LEGAL_AGREEMENT, input("2026-01", false));
        legal.publish(LegalDocumentKind.CONFIDENTIALITY_NOTICE, input("2026-01", false));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        clock.fixAt(FixedClockConfig.SEED_NOW);
    }

    private static VersionInput input(String version, boolean reconsent) {
        return new VersionInput(version, Map.of("de", "Kurz"), Map.of("de", "Text " + version, "en", "Text EN"), reconsent);
    }

    private Consent consent(User user, LegalDocumentKind kind) {
        return legal.consents(user).stream().filter(c -> c.kind() == kind).findFirst().orElseThrow();
    }

    @Test
    void requiredKindsDependOnTheRole() {
        assertThat(legal.pendingConsents(foodsharer.getId())).containsExactly(LegalDocumentKind.PRIVACY_POLICY);
        assertThat(legal.pendingConsents(foodsaver.getId()))
                .containsExactly(LegalDocumentKind.PRIVACY_POLICY, LegalDocumentKind.LEGAL_AGREEMENT);
        assertThat(legal.pendingConsents(orga.getId())).containsExactlyInAnyOrder(LegalDocumentKind.PRIVACY_POLICY,
                LegalDocumentKind.LEGAL_AGREEMENT, LegalDocumentKind.CONFIDENTIALITY_NOTICE);
        foodsharer.setQuizRole(Role.FOODSAVER);
        users.saveAndFlush(foodsharer);
        assertThat(legal.pendingConsents(foodsharer.getId())).contains(LegalDocumentKind.LEGAL_AGREEMENT);
    }

    @Test
    void acceptingTheCurrentVersionClearsThePendingConsent() {
        TestAuth.login(TestUsers.principal(foodsaver));
        Consent consent = legal.accept(LegalDocumentKind.PRIVACY_POLICY, "2026-01");
        assertThat(consent.pending()).isFalse();
        assertThat(consent.acceptedVersion()).isEqualTo("2026-01");
        assertThat(legal.hasCurrentConsent(foodsaver.getId(), LegalDocumentKind.PRIVACY_POLICY)).isTrue();
        assertThat(legal.pendingConsents(foodsaver.getId())).containsExactly(LegalDocumentKind.LEGAL_AGREEMENT);
        // accepting twice is harmless
        legal.accept(LegalDocumentKind.PRIVACY_POLICY, "2026-01");
        assertThatThrownBy(() -> legal.accept(LegalDocumentKind.PRIVACY_POLICY, "1999-01"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
    }

    @Test
    void newVersionsNeedConsentOnlyWhenTheyRequireIt() {
        TestAuth.login(TestUsers.principal(foodsaver));
        legal.accept(LegalDocumentKind.PRIVACY_POLICY, "2026-01");

        clock.fixAt(FixedClockConfig.SEED_NOW.plus(Duration.ofDays(1)));
        TestAuth.login(TestUsers.principal(orga));
        legal.publish(LegalDocumentKind.PRIVACY_POLICY, input("2026-02", false));
        assertThat(consent(foodsaver, LegalDocumentKind.PRIVACY_POLICY).pending()).isFalse();
        assertThat(consent(foodsaver, LegalDocumentKind.PRIVACY_POLICY).currentVersion()).isEqualTo("2026-02");

        TestAuth.login(TestUsers.principal(foodsaver));
        assertThatThrownBy(() -> legal.accept(LegalDocumentKind.PRIVACY_POLICY, "2026-01"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.LEGAL_VERSION_OUTDATED);
                    assertThat(e.getArgs()).containsEntry("current", "2026-02");
                });

        clock.fixAt(FixedClockConfig.SEED_NOW.plus(Duration.ofDays(2)));
        TestAuth.login(TestUsers.principal(orga));
        legal.publish(LegalDocumentKind.PRIVACY_POLICY, input("2026-03", true));
        assertThat(consent(foodsaver, LegalDocumentKind.PRIVACY_POLICY).pending()).isTrue();
        assertThat(legal.hasCurrentConsent(foodsaver.getId(), LegalDocumentKind.PRIVACY_POLICY)).isFalse();
        assertThatThrownBy(() -> legal.publish(LegalDocumentKind.PRIVACY_POLICY, input("2026-03", false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.LEGAL_VERSION_EXISTS));
        TestAuth.login(TestUsers.principal(foodsharer));
        assertThatThrownBy(() -> legal.publish(LegalDocumentKind.PRIVACY_POLICY, input("2026-04", false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }

    @Test
    void documentsFallBackToGerman() {
        var english = legal.document(LegalDocumentKind.PRIVACY_POLICY, "en", null);
        assertThat(english.body()).isEqualTo("Text EN");
        assertThat(english.fallback()).isFalse();
        var french = legal.document(LegalDocumentKind.PRIVACY_POLICY, "fr", null);
        assertThat(french.language()).isEqualTo("de");
        assertThat(french.fallback()).isTrue();
        assertThat(french.summary()).isEqualTo("Kurz");
    }

    @Test
    void contentPagesFallBackAndOnlyEditorsWrite() {
        content.upsert("rules", new ContentService.PageInput(Map.of("de", "Regeln", "fr", "Règles"),
                Map.of("de", "## Regeln\n\nText", "fr", "## Règles\n\nTexte"),
                network.foodsharing.content.domain.ContentPageKind.LEARNING_CHAPTER, 1, 2, true, null));
        content.upsert("draft", new ContentService.PageInput(Map.of("de", "Entwurf"), Map.of("de", "x"),
                network.foodsharing.content.domain.ContentPageKind.PAGE, 1, null, false, null));
        assertThat(content.page("rules", "fr").title()).isEqualTo("Règles");
        assertThat(content.page("rules", "it").fallback()).isTrue();
        assertThat(content.learningChapters("en")).singleElement()
                .satisfies(chapter -> assertThat(chapter.title()).isEqualTo("Regeln"));
        assertThatThrownBy(() -> content.page("draft", "de")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.CONTENT_NOT_FOUND));
        assertThat(ContentService.summary("## Titel\n\n**fett** und [Link](x)")).isEqualTo("Titel fett und Link(x)");
        TestAuth.login(TestUsers.principal(foodsaver));
        assertThatThrownBy(() -> content.allPages(null)).isInstanceOf(ApiException.class);
    }
}
