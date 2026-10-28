package network.foodsharing.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.calendar.domain.CalendarToken;
import network.foodsharing.calendar.repository.CalendarTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.ics.IcsWriter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.event.domain.InvitationStatus;
import network.foodsharing.event.service.EventIcsService;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.pickup.service.PickupUserService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A11: feed filters, reminders, the 14-day history window and unknown tokens. */
class CalendarFeedServiceTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final UUID USER = UUID.randomUUID();
    static final UUID STORE = UUID.randomUUID();

    CalendarTokenRepository tokens = mock(CalendarTokenRepository.class);
    PickupUserService pickups = mock(PickupUserService.class);
    IntroPickupBookingService intro = mock(IntroPickupBookingService.class);
    EventIcsService events = mock(EventIcsService.class);
    UserService users = mock(UserService.class);
    AppProperties properties = new AppProperties();
    CalendarFeedService service;

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("https://foodsharing.example");
        service = new CalendarFeedService(tokens, pickups, intro, events, users, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        CalendarToken token = new CalendarToken();
        token.setUserId(USER);
        token.setToken("abc");
        when(tokens.findByToken("abc")).thenReturn(Optional.of(token));
        when(users.findActive(USER)).thenReturn(Optional.of(new User()));
        when(pickups.calendarPickups(eq(USER), any())).thenReturn(List.of(new PickupUserService.CalendarPickup(STORE,
                "Bio-Laden", "Seestrasse 1, 8002 Zürich", NOW.plusSeconds(3600), true)));
        when(events.calendarEvents(eq(USER), anySet(), any())).thenReturn(List.of(new IcsWriter.IcsEvent(
                "event-1@foodsharing.network", NOW.plusSeconds(7200), NOW.plusSeconds(9000), "Treffen", null, null,
                null, NOW)));
    }

    private CalendarFeedService.Options options(String events, boolean pickups, boolean history, String reminders) {
        return new CalendarFeedService.Options(CalendarFeedService.EventFilter.parse(events), pickups, true, history,
                CalendarFeedService.parseReminders(reminders));
    }

    @Test
    void feedContainsPickupsAndEventsWithAlarms() {
        String feed = service.feed("abc", options(null, true, false, "15,60")).replace("\r\n ", "");
        assertThat(feed).contains("X-WR-CALNAME:foodsaving")
                .contains("UID:pickup-" + STORE + "-" + NOW.plusSeconds(3600).getEpochSecond()
                        + "@foodsharing.example")
                .contains("SUMMARY:Abholung Bio-Laden").contains("DTEND:20261110T113000Z")
                .contains("UID:event-1@foodsharing.example").contains("TRIGGER:-PT15M").contains("TRIGGER:-PT60M");
        assertThat(feed.split("BEGIN:VALARM", -1)).hasSize(5);
        verify(events).calendarEvents(USER, EnumSet.of(InvitationStatus.MAYBE, InvitationStatus.ACCEPTED), NOW);
    }

    @Test
    void filtersAndHistoryWindow() {
        String feed = service.feed("abc", options("none", false, true, null));
        assertThat(feed).doesNotContain("Abholung").doesNotContain("VALARM");
        verify(events).calendarEvents(USER, EnumSet.noneOf(InvitationStatus.class), NOW.minusSeconds(14 * 86400));
        verify(intro).bookingsOf(eq(USER), eq(NOW.minusSeconds(14 * 86400)), any());
        service.feed("abc", options("accepted", true, false, null));
        verify(events).calendarEvents(USER, EnumSet.of(InvitationStatus.ACCEPTED), NOW);
        verify(pickups).calendarPickups(USER, NOW);
    }

    @Test
    void invalidOptionsAndUnknownTokens() {
        assertThatThrownBy(() -> CalendarFeedService.parseReminders("15,abc")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CalendarFeedService.parseReminders("-5")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CalendarFeedService.parseReminders("1,2,3,4,5,6")).isInstanceOf(ApiException.class);
        assertThat(CalendarFeedService.parseReminders(" 30 , 30")).containsExactly(30);
        assertThatThrownBy(() -> CalendarFeedService.EventFilter.parse("sometimes")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.feed("nope", options(null, true, false, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.CALENDAR_TOKEN_NOT_FOUND));
        when(users.findActive(USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.feed("abc", options(null, true, false, null)))
                .isInstanceOf(ApiException.class);
    }
}
