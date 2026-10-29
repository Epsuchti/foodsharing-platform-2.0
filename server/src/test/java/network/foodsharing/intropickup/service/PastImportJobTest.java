package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupStoreAliasRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D16.3 (Zürich run): large EinAb imports report through a job that always ends (DONE, or FAILED on an error). */
class PastImportJobTest {

    static final UUID REGION = UUID.randomUUID();
    static final UUID JOB = UUID.randomUUID();

    RegionService regions = mock(RegionService.class);
    IntroPickupAdminService admin = mock(IntroPickupAdminService.class);
    UserService users = mock(UserService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    ProgressTracker tracker = mock(ProgressTracker.class);
    IntroPickupViews views = mock(IntroPickupViews.class);
    PastImportService service;
    User user = new User();
    String csv;

    @BeforeEach
    void setUp() {
        service = new PastImportService(regions, mock(RegionPermissionResolver.class), mock(IntroPickupRepository.class),
                mock(IntroPickupSlotRepository.class), mock(IntroPickupStoreAliasRepository.class), admin, users,
                currentActor, mock(HistoryService.class, RETURNS_DEEP_STUBS), tracker, views, new AppProperties());
        user.setId(UUID.randomUUID());
        user.setFoodsaverNumber(42L);
        when(regions.zoneOf(REGION)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION));
        when(regions.activeMembersAmong(anyCollection(), anyCollection())).thenReturn(Set.of(user.getId()));
        when(users.findByFoodsaverNumbers(anyCollection())).thenReturn(List.of(user));
        when(views.now()).thenReturn(Instant.parse("2026-11-02T09:00:00Z"));
        when(currentActor.requireUserId()).thenReturn(UUID.randomUUID());
        when(tracker.start(any(), anyInt())).thenReturn(JOB);
        StringBuilder text = new StringBuilder("ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback\n");
        LocalDate day = LocalDate.of(2024, 1, 1);
        for (int i = 0; i <= PastImportService.JOB_THRESHOLD; i++) {
            LocalDate date = day.plusDays(i);
            text.append("42;").append(date.getDayOfMonth()).append('.').append(date.getMonthValue()).append('.')
                    .append(date.getYear()).append(";1;Laden;Supermarkt;Guide;nein;\n");
        }
        csv = text.toString();
    }

    @Test
    void largeImportCompletesItsJob() {
        when(admin.createPastBooking(any(), any(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean())).thenReturn(new IntroPickupSlot());
        var result = service.confirm(REGION, csv);
        assertThat(result.jobId()).isEqualTo(JOB);
        assertThat(result.created()).isEqualTo(PastImportService.JOB_THRESHOLD + 1);
        verify(tracker).complete(JOB, null);
        verify(tracker, never()).fail(any(), any());
    }

    @Test
    void failingLargeImportFailsItsJobInsteadOfLeavingItRunning() {
        AtomicInteger calls = new AtomicInteger();
        when(admin.createPastBooking(any(), any(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean())).thenAnswer(inv -> {
                    if (calls.incrementAndGet() == 120) {
                        throw new IllegalStateException("database gone");
                    }
                    return new IntroPickupSlot();
                });
        assertThatThrownBy(() -> service.confirm(REGION, csv)).isInstanceOf(IllegalStateException.class);
        verify(tracker).fail(JOB, "IMPORT_FAILED");
        verify(tracker, never()).complete(any(), any());
    }
}
