package network.foodsharing.calendar.web;

import network.foodsharing.api.PublicCalendarApi;
import network.foodsharing.calendar.service.CalendarFeedService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The personal ICS feed, authorised by the token in the URL (A11). */
@RestController
public class PublicCalendarController implements PublicCalendarApi {

    private static final MediaType CALENDAR = new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8);

    private final CalendarFeedService feeds;

    public PublicCalendarController(CalendarFeedService feeds) {
        this.feeds = feeds;
    }

    @Override
    public ResponseEntity<String> getCalendarFeed(String token, String events, Boolean pickups, Boolean introPickups,
                                                  Boolean history, String reminders) {
        String feed = feeds.feed(token, new CalendarFeedService.Options(CalendarFeedService.EventFilter.parse(events),
                !Boolean.FALSE.equals(pickups), !Boolean.FALSE.equals(introPickups), Boolean.TRUE.equals(history),
                CalendarFeedService.parseReminders(reminders)));
        return ResponseEntity.ok().contentType(CALENDAR)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"foodsharing.ics\"")
                .body(feed);
    }
}
