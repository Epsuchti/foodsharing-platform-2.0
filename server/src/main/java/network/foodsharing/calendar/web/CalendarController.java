package network.foodsharing.calendar.web;

import network.foodsharing.api.CalendarApi;
import network.foodsharing.api.model.CalendarToken;
import network.foodsharing.calendar.service.CalendarTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The current user's calendar feed token (A11). */
@RestController
public class CalendarController implements CalendarApi {

    private final CalendarTokenService tokens;

    public CalendarController(CalendarTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public ResponseEntity<Void> deleteCalendarToken() {
        tokens.delete();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CalendarToken> getCalendarToken() {
        return ResponseEntity.ok(toModel(tokens.get()));
    }

    @Override
    public ResponseEntity<CalendarToken> regenerateCalendarToken() {
        return ResponseEntity.ok(toModel(tokens.regenerate()));
    }

    private static CalendarToken toModel(CalendarTokenService.Feed feed) {
        return new CalendarToken().token(feed.token()).url(feed.url());
    }
}
