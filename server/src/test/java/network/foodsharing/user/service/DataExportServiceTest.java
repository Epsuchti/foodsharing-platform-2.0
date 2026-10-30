package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.common.seed.InMemoryState;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

/** A21 export cooldown; the dev seed reset clears it (A59). */
class DataExportServiceTest {

    private final UUID me = UUID.randomUUID();
    private final DataExportService service = service();

    @Test
    void secondExportWithinTheCooldownIsRejected() {
        service.exportMine();

        assertThatThrownBy(service::exportMine).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus().value()).isEqualTo(429);
    }

    @Test
    void seedResetForgetsTheCooldown() {
        assertThat(service).isInstanceOf(InMemoryState.class);
        service.exportMine();

        service.resetInMemoryState();

        assertThat(service.exportMine()).isNotNull();
    }

    private DataExportService service() {
        User user = new User();
        user.setId(me);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(me)).thenReturn(Optional.of(user));
        HistoryQueryService historyQuery = mock(HistoryQueryService.class);
        when(historyQuery.myActivity(any())).thenReturn(Page.empty());
        UploadService uploads = mock(UploadService.class);
        when(uploads.ownedBy(me)).thenReturn(List.of());
        CurrentActorService actors = mock(CurrentActorService.class);
        when(actors.requireUserId()).thenReturn(me);
        ProgressTracker tracker = mock(ProgressTracker.class);
        when(tracker.start(any(), anyInt())).thenAnswer(invocation -> UUID.randomUUID());
        return new DataExportService(users, List.of(), historyQuery, mock(HistoryService.class, RETURNS_DEEP_STUBS),
                uploads, actors, tracker, Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));
    }
}
