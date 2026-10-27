package network.foodsharing.intropickup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.intropickup.domain.GuideProfile;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.GuideProfileRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * iCalendar output (reference {@code TeacherIcalFeedService}, RFC 5545 escaping): one EinAb for its booked users and
 * guide, and the personal feed of a guide (own EinAbs with at least one booking) behind a secret token.
 */
@Service
public class IntroPickupIcalService {

    static final Duration DURATION = Duration.ofHours(1);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Set<SlotStatus> BOOKED = Set.of(SlotStatus.BOOKED, SlotStatus.DONE);

    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final GuideProfileRepository profiles;
    private final IntroPickupViews views;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final Clock clock;

    public IntroPickupIcalService(IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                                  GuideProfileRepository profiles, IntroPickupViews views,
                                  CurrentActorService currentActor, PermissionService permissions, Clock clock) {
        this.pickups = pickups;
        this.slots = slots;
        this.profiles = profiles;
        this.views = views;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.clock = clock;
    }

    /**
     * One EinAb as a calendar file: users with a booking, the guide, ORGA.
     *
     * @throws ApiException 404 INTRO_PICKUP_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public String pickupIcs(UUID introPickupId) {
        IntroPickup pickup = views.requirePickup(introPickupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean allowed = actor.userId().equals(pickup.getGuideId()) || permissions.isOrga(actor)
                || slots.findByPickup(pickup.getId()).stream().anyMatch(slot ->
                actor.userId().equals(slot.getBookingUserId()) && BOOKED.contains(slot.getStatus()));
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return render(List.of(pickup));
    }

    /** @throws ApiException 404 RESOURCE_NOT_FOUND for an unknown token */
    @Transactional(readOnly = true)
    public String guideFeed(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        String value = token.endsWith(".ics") ? token.substring(0, token.length() - 4) : token;
        GuideProfile profile = profiles.findByIcalFeedToken(value)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
        List<IntroPickup> own = pickups.findByGuide(profile.getUserId());
        List<UUID> withBookings = own.isEmpty() ? List.of() : slots.findByPickups(own.stream()
                .map(IntroPickup::getId).toList()).stream().filter(slot -> BOOKED.contains(slot.getStatus()))
                .map(IntroPickupSlot::getIntroPickupId).distinct().toList();
        return render(own.stream().filter(p -> withBookings.contains(p.getId()))
                .sorted(Comparator.comparing(IntroPickup::getStartAt)).toList());
    }

    String render(Collection<IntroPickup> list) {
        Instant now = clock.instant();
        StringBuilder feed = new StringBuilder()
                .append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//foodsharing.network//EinAb//DE\r\n")
                .append("CALSCALE:GREGORIAN\r\n")
                .append("METHOD:PUBLISH\r\n");
        for (IntroPickup pickup : list) {
            feed.append("BEGIN:VEVENT\r\n")
                    .append("UID:").append(pickup.getId()).append("@foodsharing.network\r\n")
                    .append("DTSTAMP:").append(STAMP.format(now)).append("\r\n")
                    .append("DTSTART:").append(STAMP.format(pickup.getStartAt())).append("\r\n")
                    .append("DTEND:").append(STAMP.format(pickup.getStartAt().plus(DURATION))).append("\r\n");
            property(feed, "SUMMARY", summary(pickup));
            property(feed, "LOCATION", location(pickup));
            property(feed, "DESCRIPTION", description(pickup));
            property(feed, "URL", pickup.getOnlineCallLink());
            feed.append("STATUS:CONFIRMED\r\n").append("END:VEVENT\r\n");
        }
        return feed.append("END:VCALENDAR\r\n").toString();
    }

    private static void property(StringBuilder feed, String name, String value) {
        if (value != null && !value.isBlank()) {
            feed.append(name).append(':').append(escape(value.trim())).append("\r\n");
        }
    }

    private static String summary(IntroPickup pickup) {
        String category = "EinAb " + pickup.getCategory().name();
        return pickup.getBusinessName() == null || pickup.getBusinessName().isBlank() ? category
                : pickup.getBusinessName().trim() + " (" + category + ")";
    }

    private static String location(IntroPickup pickup) {
        return pickup.getOnlineCallLink() != null ? pickup.getOnlineCallLink() : pickup.getPrivateAddress();
    }

    private static String description(IntroPickup pickup) {
        String privateInfo = pickup.getPrivateInfo() == null || pickup.getPrivateInfo().isBlank() ? null
                : pickup.getPrivateInfo().trim();
        String publicInfo = pickup.getPublicInfo() == null || pickup.getPublicInfo().isBlank() ? null
                : pickup.getPublicInfo().trim();
        if (privateInfo == null) {
            return publicInfo;
        }
        return publicInfo == null ? privateInfo : privateInfo + "\n\n" + publicInfo;
    }

    /** RFC 5545 TEXT escaping. */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
                .replace(";", "\\;").replace(",", "\\,");
    }
}
