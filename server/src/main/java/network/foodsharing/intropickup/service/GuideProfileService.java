package network.foodsharing.intropickup.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.GuideProfile;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.repository.GuideProfileRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.PhoneNumberValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guide settings (plan §14.1 EA16): the link of the guide's own foodsharing calendar (https on allowed hosts only,
 * SSRF-safe), a phone number for booked users, and the personal iCal feed (token created on first request).
 */
@Service
public class GuideProfileService {

    public static final String FEED_PATH = "/api/public/intro-pickups/ical/";

    public record ProfileView(String icalLink, String phone, String feedUrl) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GuideProfileRepository profiles;
    private final IcalImportService icalImport;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final AppProperties properties;

    public GuideProfileService(GuideProfileRepository profiles, IcalImportService icalImport,
                               CurrentActorService currentActor, PermissionService permissions,
                               HistoryService history, AppProperties properties) {
        this.profiles = profiles;
        this.icalImport = icalImport;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ProfileView profile() {
        UUID userId = requireGuide();
        return profiles.findByUserId(userId).map(this::view).orElseGet(() -> new ProfileView(null, null, null));
    }

    /** @throws ApiException 400 ICAL_LINK_NOT_ALLOWED / INVALID_PHONE_NUMBER, 403 ACCESS_DENIED */
    @Transactional
    public ProfileView update(String icalLink, String phone) {
        UUID userId = requireGuide();
        String link = icalLink == null || icalLink.isBlank() ? null : icalLink.trim();
        if (link != null && !icalImport.isAllowedLink(link)) {
            throw ApiException.badRequest(ApiErrorCode.ICAL_LINK_NOT_ALLOWED);
        }
        String number = phone == null || phone.isBlank() ? null : phone.trim();
        if (number != null && !PhoneNumberValidator.isValid(number)) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_PHONE_NUMBER);
        }
        GuideProfile profile = profileOf(userId);
        profile.setIcalLink(link);
        profile.setPhone(number == null ? null : PhoneNumberValidator.normalize(number));
        profiles.save(profile);
        history.record(history.event(IntroPickupAction.GUIDE_PROFILE_UPDATED, userId)
                .metadata("icalLink", link != null).metadata("phone", number != null).build());
        return view(profile);
    }

    /** The personal feed URL; creates the token on first use. */
    @Transactional
    public String feedUrl() {
        UUID userId = requireGuide();
        GuideProfile profile = profileOf(userId);
        if (profile.getIcalFeedToken() == null) {
            String token;
            do {
                byte[] bytes = new byte[24];
                RANDOM.nextBytes(bytes);
                token = HexFormat.of().formatHex(bytes);
            } while (profiles.existsByIcalFeedToken(token));
            profile.setIcalFeedToken(token);
            profiles.save(profile);
            history.record(history.event(IntroPickupAction.GUIDE_PROFILE_UPDATED, userId)
                    .metadata("feedToken", "created").build());
        }
        return url(profile.getIcalFeedToken());
    }

    /** Future events of the guide's own calendar. @throws ApiException 400 ICAL_LINK_NOT_ALLOWED */
    @Transactional(readOnly = true)
    public List<IcalImportService.Candidate> candidates() {
        UUID userId = requireGuide();
        String link = profiles.findByUserId(userId).map(GuideProfile::getIcalLink).orElse(null);
        if (link == null) {
            return List.of();
        }
        if (!icalImport.isAllowedLink(link)) {
            throw ApiException.badRequest(ApiErrorCode.ICAL_LINK_NOT_ALLOWED);
        }
        return icalImport.candidates(link);
    }

    /** Phone of a guide for booked users (null when none). */
    @Transactional(readOnly = true)
    public String phoneOf(UUID guideId) {
        return profiles.findByUserId(guideId).map(GuideProfile::getPhone).orElse(null);
    }

    private GuideProfile profileOf(UUID userId) {
        return profiles.findByUserId(userId).orElseGet(() -> {
            GuideProfile created = new GuideProfile();
            created.setUserId(userId);
            return created;
        });
    }

    private ProfileView view(GuideProfile profile) {
        return new ProfileView(profile.getIcalLink(), profile.getPhone(),
                profile.getIcalFeedToken() == null ? null : url(profile.getIcalFeedToken()));
    }

    private String url(String token) {
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        return base + FEED_PATH + token + ".ics";
    }

    private UUID requireGuide() {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.hasAnywhere(actor, Permission.CAN_GIVE_INTRO_PICKUPS)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return actor.userId();
    }
}
