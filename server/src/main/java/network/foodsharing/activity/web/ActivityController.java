package network.foodsharing.activity.web;

import network.foodsharing.api.ActivityApi;
import network.foodsharing.api.model.ActivityPage;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class ActivityController implements ActivityApi {

    @Override
    public ResponseEntity<ActivityPage> getActivityFeed(Integer page, Integer size) {
        throw ApiException.notImplemented("getActivityFeed");
    }
}
