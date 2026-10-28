package network.foodsharing.calendar.web;

import network.foodsharing.api.PublicCalendarApi;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class PublicCalendarController implements PublicCalendarApi {

    @Override
    public ResponseEntity<String> getCalendarFeed(String token, String events, Boolean pickups, Boolean introPickups, Boolean history, String reminders) {
        throw ApiException.notImplemented("getCalendarFeed");
    }
}
