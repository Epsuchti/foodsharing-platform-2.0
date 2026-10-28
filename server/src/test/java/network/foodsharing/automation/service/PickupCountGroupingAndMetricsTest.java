package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.AdvertStatus;
import network.foodsharing.automation.domain.CancellationStatus;
import network.foodsharing.automation.domain.MetricDailyCount;
import network.foodsharing.automation.domain.OpenSlotAdvertAudit;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.SignoutCancellation;
import network.foodsharing.automation.repository.MetricDailyCountRepository;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.RequestPolicyAuditRepository;
import network.foodsharing.automation.repository.SignoutCancellationRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupExtraSlot;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Pickup-count grouping and the metric archive (AU7, M4 B5). */
class PickupCountGroupingAndMetricsTest {

    static final UUID REGION = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-20T03:15:00Z");
    static final Instant OLD = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void usersAreGroupedByTheirNumberOfPickupsDescending() {
        Store a = store("A");
        Store b = store("B");
        User anna = user("Anna");
        User ben = user("Ben");
        User cleo = user("Cleo");
        List<PickupSignup> signups = List.of(signup(anna, a, 1), signup(anna, b, 2), signup(ben, a, 3),
                signup(cleo, b, 4), signup(anna, a, 5));
        var groups = PickupCountGroupingService.group(signups, Map.of(a.getId(), a, b.getId(), b),
                ids -> List.of(anna, ben, cleo));
        assertThat(groups).extracting(PickupCountGroupingService.Group::pickupCount).containsExactly(3, 1);
        assertThat(groups.getFirst().users()).singleElement().satisfies(u -> {
            assertThat(u.user()).isEqualTo(anna);
            assertThat(u.stores()).containsExactly(a, b);
        });
        assertThat(groups.get(1).users()).extracting(u -> u.user().getDisplayName()).containsExactly("Ben", "Cleo");
    }

    @Test
    void archiveCountsThenDeletesAndAddsToExistingRows() {
        PickupPolicyAuditRepository pickupAudits = mock(PickupPolicyAuditRepository.class);
        RequestPolicyAuditRepository requestAudits = mock(RequestPolicyAuditRepository.class);
        OpenSlotAdvertAuditRepository advertAudits = mock(OpenSlotAdvertAuditRepository.class);
        SignoutCancellationRepository cancellations = mock(SignoutCancellationRepository.class);
        PickupAutomationQueries pickups = mock(PickupAutomationQueries.class);
        MetricDailyCountRepository archive = mock(MetricDailyCountRepository.class);
        RegionService regions = mock(RegionService.class);
        when(regions.zoneOf(REGION)).thenReturn(ZoneId.of("Europe/Zurich"));
        AutomationAuditService audits = new AutomationAuditService(pickupAudits, requestAudits, advertAudits,
                cancellations, pickups, null, null, regions, null, null, JsonMapper.builder().build(),
                Clock.systemUTC());
        PickupPolicyAudit declined = new PickupPolicyAudit();
        declined.setRegionId(REGION);
        declined.setCreatedAt(OLD);
        declined.setDecision(PickupPolicyDecision.DECLINED);
        declined.setReasons("[{\"rule\":\"GAP\",\"text\":\"x\"},{\"rule\":\"EXPERIENCE\",\"text\":\"y\"}]");
        OpenSlotAdvertAudit filled = new OpenSlotAdvertAudit();
        filled.setRegionId(REGION);
        filled.setCreatedAt(OLD);
        filled.setStatus(AdvertStatus.SENT);
        filled.setFilledAt(OLD.plusSeconds(3600));
        OpenSlotAdvertAudit summary = new OpenSlotAdvertAudit();
        summary.setRegionId(REGION);
        summary.setCreatedAt(OLD);
        summary.setDryRun(true);
        summary.setStatus(AdvertStatus.DRY_RUN_SUMMARY);
        SignoutCancellation cancellation = new SignoutCancellation();
        cancellation.setRegionId(REGION);
        cancellation.setCreatedAt(OLD);
        cancellation.setStatus(CancellationStatus.NOT_REPLACED);
        PickupExtraSlot extra = new PickupExtraSlot();
        extra.setRegionId(REGION);
        extra.setCreatedAt(OLD);
        when(pickupAudits.findByCreatedAtBefore(any())).thenReturn(List.of(declined));
        when(advertAudits.findByCreatedAtBefore(any())).thenReturn(List.of(filled, summary));
        when(cancellations.findByCreatedAtBefore(any())).thenReturn(List.of(cancellation));
        when(pickups.extraSlotsCreatedBefore(any())).thenReturn(List.of(extra));
        MetricDailyCount existing = new MetricDailyCount();
        existing.setMetricValue(5);
        existing.setMetricKey("ADVERT.SENT");
        LocalDate day = LocalDate.of(2026, 11, 1);
        when(archive.findByRegionIdAndMetricDayAndMetricKey(any(), any(), any())).thenReturn(Optional.empty());
        when(archive.findByRegionIdAndMetricDayAndMetricKey(REGION, day, "ADVERT.SENT"))
                .thenReturn(Optional.of(existing));
        List<MetricDailyCount> saved = new ArrayList<>();
        when(archive.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        MetricArchiveService service = new MetricArchiveService(pickupAudits, requestAudits, advertAudits,
                cancellations, pickups, archive, audits, regions, mock(PermissionService.class),
                mock(CurrentActorService.class), mock(HistoryService.class, RETURNS_DEEP_STUBS), new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(network.foodsharing.intropickup.service.IntroPickupMetrics.class));
        assertThat(service.archiveAndDelete()).isEqualTo(5);
        assertThat(existing.getMetricValue()).isEqualTo(6);
        assertThat(saved).extracting(MetricDailyCount::getMetricKey).containsExactlyInAnyOrder("ADVERT.SENT",
                "ADVERT.FILLED_AFTER_ADVERT", "SLOT_APPROVAL.DECLINED", "SLOT_DECLINE_RULE.GAP",
                "SLOT_DECLINE_RULE.EXPERIENCE", "CANCELLATION.NOT_REPLACED", "EXTRA_SLOT.ADDED");
        verify(pickupAudits).deleteAll(List.of(declined));
        verify(advertAudits).deleteAll(List.of(filled, summary));
        verify(pickups).deleteExtraSlots(List.of(extra));
    }

    @Test
    void seriesCountsDoneIntroPickupsAndMergesTheArchive() {
        RegionService regions = mock(RegionService.class);
        when(regions.zoneOf(REGION)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION));
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.mayAdministrateRegion(any(), any())).thenReturn(true);
        var intro = mock(network.foodsharing.intropickup.service.IntroPickupMetrics.class);
        when(intro.done(any(), any(), any())).thenReturn(List.of(
                new network.foodsharing.intropickup.service.IntroPickupMetrics.Done(REGION, NOW.minusSeconds(86_400))));
        MetricDailyCountRepository archive = mock(MetricDailyCountRepository.class);
        MetricDailyCount row = new MetricDailyCount();
        row.setMetricKey("ADVERT.SENT");
        row.setMetricDay(LocalDate.of(2026, 11, 1));
        row.setMetricValue(4);
        when(archive.findByRegionIdInAndMetricDayBetween(any(), any(), any())).thenReturn(List.of(row));
        MetricArchiveService service = new MetricArchiveService(mock(PickupPolicyAuditRepository.class),
                mock(RequestPolicyAuditRepository.class), mock(OpenSlotAdvertAuditRepository.class),
                mock(SignoutCancellationRepository.class), mock(PickupAutomationQueries.class), archive,
                mock(AutomationAuditService.class), regions, permissions, mock(CurrentActorService.class),
                mock(HistoryService.class), new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC), intro);
        var series = service.series(REGION, null, null);
        assertThat(series.get("INTRO_PICKUP.DONE")).containsEntry(LocalDate.of(2026, 11, 19), 1L);
        assertThat(series.get("ADVERT.SENT")).containsEntry(LocalDate.of(2026, 11, 1), 4L);
    }

    static Store store(String name) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setName(name);
        return store;
    }

    static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        return user;
    }

    static PickupSignup signup(User user, Store store, int day) {
        PickupSignup signup = new PickupSignup();
        signup.setUserId(user.getId());
        signup.setStoreId(store.getId());
        signup.setPickupAt(NOW.plusSeconds(86_400L * day));
        return signup;
    }
}
