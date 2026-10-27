package archfixture.service;

import network.foodsharing.history.NoHistory;
import org.springframework.transaction.annotation.Transactional;

/** Fixture: explicitly exempt. */
public class ExemptService {

    private long lastSeen;

    @Transactional
    @NoHistory(reason = "heartbeat")
    public void touch() {
        lastSeen++;
    }
}
