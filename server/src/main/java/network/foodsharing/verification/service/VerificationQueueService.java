package network.foodsharing.verification.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.intropickup.service.NewcomerProgressService.Stage;
import network.foodsharing.onboarding.service.JourneyService;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The verification queue of a region (plan D10.3): unverified active members; READY = EinAb journey COMPLETE and the
 * legal agreement accepted. Sorted by the latest EinAb (newest first, none last), with the guide comments.
 */
@Service
public class VerificationQueueService {

    public enum Filter { READY, ALL_UNVERIFIED }

    public record Candidate(User user, IntroPickupProgressService.Summary progress, Instant quizPassedAt,
                            Instant agreementAcceptedAt, Instant lastIntroPickupAt, boolean learningDone,
                            List<IntroPickupProgressService.GuideComment> comments) {
    }

    private final RegionService regions;
    private final MembershipService memberships;
    private final UserService users;
    private final IntroPickupProgressService progress;
    private final LegalService legal;
    private final QuizSessionService quizzes;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final JourneyService journeys;

    public VerificationQueueService(RegionService regions, MembershipService memberships, UserService users,
                                    IntroPickupProgressService progress, LegalService legal,
                                    QuizSessionService quizzes, CurrentActorService currentActor,
                                    PermissionService permissions, JourneyService journeys) {
        this.journeys = journeys;
        this.regions = regions;
        this.memberships = memberships;
        this.users = users;
        this.progress = progress;
        this.legal = legal;
        this.quizzes = quizzes;
        this.currentActor = currentActor;
        this.permissions = permissions;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public Page<Candidate> queue(UUID regionId, Filter filter, String search, Integer page, Integer size) {
        regions.require(regionId);
        permissions.requireMayAdministrateRegion(currentActor.requirePrincipal(), regionId);
        String needle = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        List<Candidate> result = new ArrayList<>();
        for (User user : users.findActive(memberships.membersOf(regionId))) {
            if (user.isVerified() || (needle != null && !matches(user, needle))) {
                continue;
            }
            IntroPickupProgressService.Summary summary = progress.progressOf(user.getId(), regionId);
            Instant agreement = legal.consents(user).stream()
                    .filter(c -> c.kind() == LegalDocumentKind.LEGAL_AGREEMENT && !c.pending()
                            && c.acceptedVersion() != null)
                    .map(LegalService.Consent::acceptedAt).findFirst().orElse(null);
            boolean agreed = legal.hasCurrentConsent(user.getId(), LegalDocumentKind.LEGAL_AGREEMENT);
            boolean ready = summary.stage() == Stage.COMPLETE && agreed;
            if (filter != Filter.ALL_UNVERIFIED && !ready) {
                continue;
            }
            result.add(new Candidate(user, summary, quizzes.passedAt(user.getId(), QuizKind.FOODSAVER).orElse(null),
                    agreement, progress.lastIntroPickupAt(user.getId()), journeys.learningDone(user.getId()),
                    progress.commentsOn(user.getId())));
        }
        result.sort(Comparator.comparing(Candidate::lastIntroPickupAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(Math.max(1, size), 100);
        int from = Math.min(result.size(), p * s);
        return new PageImpl<>(result.subList(from, Math.min(result.size(), from + s)), PageRequest.of(p, s),
                result.size());
    }

    private static boolean matches(User user, String needle) {
        return contains(user.getDisplayName(), needle) || contains(user.getEmail(), needle)
                || contains(user.getFirstName(), needle) || contains(user.getLastName(), needle)
                || (user.getFoodsaverNumber() != null && user.getFoodsaverNumber().toString().contains(needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
