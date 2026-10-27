package archfixture.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.service.HistoryService;
import org.springframework.transaction.annotation.Transactional;

/** Fixture: records history directly. */
public class RecordingService {

    private final HistoryService history;
    private final HistoryAction action;

    public RecordingService(HistoryService history, HistoryAction action) {
        this.history = history;
        this.action = action;
    }

    @Transactional
    public void change() {
        history.record(history.event(action, UUID.randomUUID()).build());
    }
}
