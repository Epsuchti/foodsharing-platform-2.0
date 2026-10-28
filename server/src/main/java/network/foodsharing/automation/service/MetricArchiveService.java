package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AdvertStatus;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.MetricDailyCount;
import network.foodsharing.automation.domain.OpenSlotAdvertAudit;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.RequestPolicyAudit;
import network.foodsharing.automation.domain.SignoutCancellation;
import network.foodsharing.automation.repository.MetricDailyCountRepository;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.RequestPolicyAuditRepository;
import network.foodsharing.automation.repository.SignoutCancellationRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupExtraSlot;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonymous daily automation metrics (AU7, port of the reference metric archive): audits older than
 * {@code audit-retention-days} (cancellations and extra slots: {@code signout-retention-days}) are counted per region
 * (the store's region), day (in the region's zone) and key, added to {@code metric_daily_count} and deleted.
 * {@link #series} merges the archive with the live rows, so every row is counted exactly once.
 */
@Service
public class MetricArchiveService {

    static final String ADVERT_SENT = "ADVERT.SENT";
    static final String ADVERT_FILLED = "ADVERT.FILLED_AFTER_ADVERT";
    static final String EXTRA_SLOT_ADDED = "EXTRA_SLOT.ADDED";
    static final int MAX_DAYS = 400;

    /** Key of a daily count. */
    public record Key(UUID regionId, LocalDate day, String key) {
    }

    private final PickupPolicyAuditRepository pickupAudits;
    private final RequestPolicyAuditRepository requestAudits;
    private final OpenSlotAdvertAuditRepository advertAudits;
    private final SignoutCancellationRepository cancellations;
    private final PickupAutomationQueries pickups;
    private final MetricDailyCountRepository archive;
    private final AutomationAuditService audits;
    private final RegionService regions;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public MetricArchiveService(PickupPolicyAuditRepository pickupAudits, RequestPolicyAuditRepository requestAudits,
                                OpenSlotAdvertAuditRepository advertAudits, SignoutCancellationRepository cancellations,
                                PickupAutomationQueries pickups, MetricDailyCountRepository archive,
                                AutomationAuditService audits, RegionService regions, PermissionService permissions,
                                CurrentActorService currentActor, HistoryService history, AppProperties properties,
                                Clock clock) {
        this.pickupAudits = pickupAudits;
        this.requestAudits = requestAudits;
        this.advertAudits = advertAudits;
        this.cancellations = cancellations;
        this.pickups = pickups;
        this.archive = archive;
        this.audits = audits;
        this.regions = regions;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** Nightly: archive, then delete. @return number of archived rows */
    @Transactional
    public int archiveAndDelete() {
        Instant now = clock.instant();
        Instant auditCutoff = now.minus(Duration.ofDays(properties.getAutomation().getAuditRetentionDays()));
        Instant longCutoff = now.minus(Duration.ofDays(properties.getAutomation().getSignoutRetentionDays()));
        Map<UUID, ZoneId> zones = new HashMap<>();
        Function<UUID, ZoneId> zone = id -> zones.computeIfAbsent(id, regions::zoneOf);
        List<PickupPolicyAudit> pickupRows = pickupAudits.findByCreatedAtBefore(auditCutoff);
        List<RequestPolicyAudit> requestRows = requestAudits.findByCreatedAtBefore(auditCutoff);
        List<OpenSlotAdvertAudit> advertRows = advertAudits.findByCreatedAtBefore(auditCutoff);
        List<SignoutCancellation> cancellationRows = cancellations.findByCreatedAtBefore(longCutoff);
        List<PickupExtraSlot> extraRows = pickups.extraSlotsCreatedBefore(longCutoff);
        Map<Key, Long> counts = count(pickupRows, requestRows, advertRows, cancellationRows, extraRows, zone);
        counts.forEach((key, value) -> {
            MetricDailyCount row = archive.findByRegionIdAndMetricDayAndMetricKey(key.regionId(), key.day(),
                    key.key()).orElseGet(() -> {
                        MetricDailyCount created = new MetricDailyCount();
                        created.setRegionId(key.regionId());
                        created.setMetricDay(key.day());
                        created.setMetricKey(key.key());
                        return created;
                    });
            row.setMetricValue(row.getMetricValue() + value);
            archive.save(row);
        });
        pickupAudits.deleteAll(pickupRows);
        requestAudits.deleteAll(requestRows);
        advertAudits.deleteAll(advertRows);
        cancellations.deleteAll(cancellationRows);
        pickups.deleteExtraSlots(extraRows);
        int archived = pickupRows.size() + requestRows.size() + advertRows.size() + cancellationRows.size()
                + extraRows.size();
        if (archived > 0) {
            history.record(history.event(AutomationAction.AUDITS_ARCHIVED, HistoryService.SYSTEM_SUBJECT_ID)
                    .actorAutomation("audit-retention").metadata("rows", archived).metadata("counts", counts.size())
                    .build());
        }
        return archived;
    }

    /**
     * Daily series per key for the region subtree (archive + live rows), default the last 30 days.
     *
     * @throws ApiException 400 VALIDATION_FAILED, 403 unless ambassador or CAN_USE_AUTOMATIONS, 404 REGION_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public Map<String, Map<LocalDate, Long>> series(UUID regionId, LocalDate from, LocalDate to) {
        regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.mayAdministrateRegion(actor, regionId)
                && !permissions.has(actor, Permission.CAN_USE_AUTOMATIONS, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        ZoneId zone = regions.zoneOf(regionId);
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        LocalDate toDay = to == null ? today : to;
        LocalDate fromDay = from == null ? toDay.minusDays(29) : from;
        if (fromDay.isAfter(toDay) || fromDay.plusDays(MAX_DAYS).isBefore(toDay)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "from");
        }
        List<UUID> regionIds = regions.subtreeIds(regionId);
        Instant start = fromDay.atStartOfDay(zone).toInstant();
        Instant end = toDay.plusDays(1).atStartOfDay(zone).toInstant();
        Map<Key, Long> live = count(
                pickupAudits.findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(regionIds, start, end),
                requestAudits.findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(regionIds, start, end),
                advertAudits.findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(regionIds, start, end),
                cancellations.findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(regionIds, start, end),
                pickups.extraSlotsInRegions(regionIds, start, end), id -> zone);
        Map<String, Map<LocalDate, Long>> series = new TreeMap<>();
        live.forEach((key, value) -> series.computeIfAbsent(key.key(), k -> new TreeMap<>())
                .merge(key.day(), value, Long::sum));
        for (MetricDailyCount row : archive.findByRegionIdInAndMetricDayBetween(regionIds, fromDay, toDay)) {
            series.computeIfAbsent(row.getMetricKey(), k -> new TreeMap<>()).merge(row.getMetricDay(),
                    row.getMetricValue(), Long::sum);
        }
        return series;
    }

    Map<Key, Long> count(Collection<PickupPolicyAudit> pickupRows, Collection<RequestPolicyAudit> requestRows,
                         Collection<OpenSlotAdvertAudit> advertRows, Collection<SignoutCancellation> cancellationRows,
                         Collection<PickupExtraSlot> extraRows, Function<UUID, ZoneId> zone) {
        Map<Key, Long> counts = new HashMap<>();
        for (PickupPolicyAudit row : pickupRows) {
            LocalDate day = day(row.getCreatedAt(), zone.apply(row.getRegionId()));
            add(counts, row.getRegionId(), day, "SLOT_APPROVAL." + row.getDecision().name());
            String prefix = row.getDecision() == PickupPolicyDecision.DECLINED
                    || row.getDecision() == PickupPolicyDecision.WOULD_DECLINE ? "SLOT_DECLINE_RULE."
                    : row.getDecision() == PickupPolicyDecision.SKIPPED ? "SLOT_SKIP_RULE." : null;
            if (prefix != null) {
                for (PickupPolicyEvaluator.Reason reason : audits.reasons(row.getReasons())) {
                    add(counts, row.getRegionId(), day, prefix + reason.rule().name());
                }
            }
        }
        for (RequestPolicyAudit row : requestRows) {
            add(counts, row.getRegionId(), day(row.getCreatedAt(), zone.apply(row.getRegionId())),
                    "REQUEST." + row.getStatus().name());
        }
        for (OpenSlotAdvertAudit row : advertRows) {
            if (row.isDryRun() || row.getStatus() == AdvertStatus.DRY_RUN_SUMMARY) {
                continue;
            }
            LocalDate day = day(row.getCreatedAt(), zone.apply(row.getRegionId()));
            if (OpenSlotAdvertRunner.SENT_STATUSES.contains(row.getStatus())) {
                add(counts, row.getRegionId(), day, ADVERT_SENT);
                if (row.getFilledAt() != null) {
                    add(counts, row.getRegionId(), day, ADVERT_FILLED);
                }
            }
        }
        for (SignoutCancellation row : cancellationRows) {
            add(counts, row.getRegionId(), day(row.getCreatedAt(), zone.apply(row.getRegionId())),
                    "CANCELLATION." + row.getStatus().name());
        }
        for (PickupExtraSlot row : extraRows) {
            add(counts, row.getRegionId(), day(row.getCreatedAt(), zone.apply(row.getRegionId())), EXTRA_SLOT_ADDED);
        }
        return counts;
    }

    private static void add(Map<Key, Long> counts, UUID regionId, LocalDate day, String key) {
        counts.merge(new Key(regionId, day, key), 1L, Long::sum);
    }

    private static LocalDate day(Instant at, ZoneId zone) {
        return at.atZone(zone).toLocalDate();
    }

    /** Points of every series; empty series are left out. */
    public static List<Map.Entry<String, List<Map.Entry<LocalDate, Long>>>> flatten(
            Map<String, Map<LocalDate, Long>> series) {
        List<Map.Entry<String, List<Map.Entry<LocalDate, Long>>>> result = new ArrayList<>();
        series.forEach((key, points) -> result.add(Map.entry(key, new ArrayList<>(points.entrySet()))));
        return result;
    }
}
