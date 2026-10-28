package network.foodsharing.calendar.service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.calendar.domain.CalendarToken;
import network.foodsharing.calendar.repository.CalendarTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.ics.IcsWriter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.event.domain.InvitationStatus;
import network.foodsharing.event.service.EventIcsService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.pickup.service.PickupUserService;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal calendar feed (A11, upstream {@code CalendarTransactions.listAppointments}): the token owner's pickup
 * sign-ups (30 min), booked EinAbs (1 h) and events filtered by invitation status, from now on or 14 days back with
 * {@code history}; one display {@code VALARM} per reminder (minutes before, comma-separated, at most 5, each
 * 0..10080). UIDs are {@code <kind>-<id>@<domain>}.
 */
@Service
public class CalendarFeedService {

    static final String PRODUCT_ID = "-//foodsharing.network//Calendar//DE";
    static final String CALENDAR_NAME = "foodsaving";
    static final Duration PICKUP_DURATION = Duration.ofMinutes(30);
    static final Duration INTRO_PICKUP_DURATION = Duration.ofHours(1);
    static final Duration HISTORY = Duration.ofDays(14);
    static final int MAX_REMINDERS = 5;
    static final int MAX_REMINDER_MINUTES = 7 * 24 * 60;

    /** Which events: every invitation, open invitations and answers, maybe+accepted, accepted, none. */
    public enum EventFilter {
        EVERY(EnumSet.allOf(InvitationStatus.class)),
        INVITATIONS(EnumSet.of(InvitationStatus.INVITED, InvitationStatus.MAYBE, InvitationStatus.ACCEPTED)),
        MAYBE(EnumSet.of(InvitationStatus.MAYBE, InvitationStatus.ACCEPTED)),
        ACCEPTED(EnumSet.of(InvitationStatus.ACCEPTED)),
        NONE(EnumSet.noneOf(InvitationStatus.class));

        final Set<InvitationStatus> statuses;

        EventFilter(Set<InvitationStatus> statuses) {
            this.statuses = statuses;
        }

        /** Null = MAYBE (what the user attends or might attend). @throws ApiException 400 VALIDATION_FAILED */
        public static EventFilter parse(String value) {
            if (value == null || value.isBlank()) {
                return MAYBE;
            }
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "events");
            }
        }
    }

    /** Feed options; null booleans mean the contract defaults. */
    public record Options(EventFilter events, boolean pickups, boolean introPickups, boolean history,
                          List<Integer> reminders) {
    }

    private final CalendarTokenRepository tokens;
    private final PickupUserService pickups;
    private final IntroPickupBookingService introPickups;
    private final EventIcsService events;
    private final UserService users;
    private final AppProperties properties;
    private final Clock clock;

    public CalendarFeedService(CalendarTokenRepository tokens, PickupUserService pickups,
                               IntroPickupBookingService introPickups, EventIcsService events, UserService users,
                               AppProperties properties, Clock clock) {
        this.tokens = tokens;
        this.pickups = pickups;
        this.introPickups = introPickups;
        this.events = events;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    /** "15, 60" → [15, 60]. @throws ApiException 400 VALIDATION_FAILED */
    public static List<Integer> parseReminders(String value) {
        List<Integer> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(",")) {
            try {
                int minutes = Integer.parseInt(part.strip());
                if (minutes < 0 || minutes > MAX_REMINDER_MINUTES) {
                    throw new NumberFormatException(part);
                }
                if (!result.contains(minutes)) {
                    result.add(minutes);
                }
            } catch (NumberFormatException ex) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "reminders");
            }
        }
        if (result.size() > MAX_REMINDERS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "reminders");
        }
        return result;
    }

    /** @throws ApiException 404 CALENDAR_TOKEN_NOT_FOUND (unknown token or inactive owner) */
    @Transactional(readOnly = true)
    public String feed(String token, Options options) {
        CalendarToken owner = tokens.findByToken(token == null ? "" : token)
                .filter(t -> users.findActive(t.getUserId()).isPresent())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.CALENDAR_TOKEN_NOT_FOUND));
        UUID userId = owner.getUserId();
        Instant now = clock.instant();
        Instant from = options.history() ? now.minus(HISTORY) : now;
        String domain = domain();
        String base = trimSlash(properties.getBaseUrl());
        List<IcsWriter.IcsEvent> entries = new ArrayList<>();
        if (options.pickups()) {
            for (PickupUserService.CalendarPickup pickup : pickups.calendarPickups(userId, from)) {
                entries.add(new IcsWriter.IcsEvent(
                        "pickup-" + pickup.storeId() + "-" + pickup.pickupAt().getEpochSecond() + "@" + domain,
                        pickup.pickupAt(), pickup.pickupAt().plus(PICKUP_DURATION),
                        "Abholung " + pickup.storeName() + (pickup.confirmed() ? "" : " (unbestätigt)"), null,
                        pickup.address(), base + "/stores/" + pickup.storeId(), now));
            }
        }
        if (options.introPickups()) {
            for (var booking : introPickups.bookingsOf(userId, from, Instant.parse("9999-12-31T00:00:00Z"))) {
                IntroPickup pickup = booking.pickup().pickup();
                String name = pickup.getBusinessName() == null || pickup.getBusinessName().isBlank()
                        ? "Einführungsabholung" : "Einführungsabholung " + pickup.getBusinessName().strip();
                entries.add(new IcsWriter.IcsEvent("intropickup-" + pickup.getId() + "@" + domain,
                        pickup.getStartAt(), pickup.getStartAt().plus(INTRO_PICKUP_DURATION), name,
                        pickup.getPublicInfo(), pickup.getOnlineCallLink() != null ? pickup.getOnlineCallLink()
                        : pickup.getPrivateAddress(), base + "/intro-pickups/" + pickup.getId(), now));
            }
        }
        for (IcsWriter.IcsEvent event : events.calendarEvents(userId, options.events().statuses, from)) {
            entries.add(new IcsWriter.IcsEvent(event.uid().replaceFirst("@.*$", "@" + domain), event.start(),
                    event.end(), event.summary(), event.description(), event.location(), event.url(),
                    event.stamp()));
        }
        entries.sort(Comparator.comparing(IcsWriter.IcsEvent::start));
        return IcsWriter.calendar(PRODUCT_ID, CALENDAR_NAME, entries, options.reminders());
    }

    private String domain() {
        try {
            String host = URI.create(properties.getBaseUrl()).getHost();
            return host == null ? "foodsharing.network" : host;
        } catch (IllegalArgumentException ex) {
            return "foodsharing.network";
        }
    }

    private static String trimSlash(String url) {
        return url == null ? "" : url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
