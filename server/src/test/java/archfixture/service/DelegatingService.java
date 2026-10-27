package archfixture.service;

import org.springframework.transaction.annotation.Transactional;

/** Fixture: records history via another service method (one level). */
public class DelegatingService {

    private final RecordingService recordingService;

    public DelegatingService(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @Transactional
    public void change() {
        recordingService.change();
    }
}
