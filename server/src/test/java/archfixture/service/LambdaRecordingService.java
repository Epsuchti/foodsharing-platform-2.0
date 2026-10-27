package archfixture.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.service.HistoryService;
import org.springframework.transaction.annotation.Transactional;

/** Fixture: records history inside a lambda. */
public class LambdaRecordingService {

    private final HistoryService history;
    private final HistoryAction action;

    public LambdaRecordingService(HistoryService history, HistoryAction action) {
        this.history = history;
        this.action = action;
    }

    @Transactional
    public void change() {
        List.of(UUID.randomUUID()).forEach(id -> history.record(history.event(action, id).build()));
    }
}
