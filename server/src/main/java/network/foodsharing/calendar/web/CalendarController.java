package network.foodsharing.calendar.web;

import network.foodsharing.api.CalendarApi;
import network.foodsharing.api.model.CalendarToken;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class CalendarController implements CalendarApi {

    @Override
    public ResponseEntity<Void> deleteCalendarToken() {
        throw ApiException.notImplemented("deleteCalendarToken");
    }

    @Override
    public ResponseEntity<CalendarToken> getCalendarToken() {
        throw ApiException.notImplemented("getCalendarToken");
    }

    @Override
    public ResponseEntity<CalendarToken> regenerateCalendarToken() {
        throw ApiException.notImplemented("regenerateCalendarToken");
    }
}
