package network.foodsharing.pickup.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.pickup.domain.RegularPickup;
import network.foodsharing.pickup.repository.RegularPickupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The weekly schedule of a store (A20). Replacing it never touches taken slots: every sign-up already turned its
 * occurrence into a {@code pickup} row.
 */
@Service
public class RegularPickupService {

    /** One weekly pickup as entered (startTime "HH:mm"). */
    public record Entry(DayOfWeek weekday, String startTime, int slotCount, String description) {
    }

    private static final Comparator<RegularPickup> ORDER = Comparator.comparing(RegularPickup::getWeekday)
            .thenComparing(RegularPickup::getStartTime);

    private final RegularPickupRepository regular;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    public RegularPickupService(RegularPickupRepository regular, StoreService stores, StorePermissionService rights,
                                CurrentActorService currentActor, HistoryService history,
                                ApplicationEventPublisher events, AppProperties properties) {
        this.regular = regular;
        this.stores = stores;
        this.rights = rights;
        this.currentActor = currentActor;
        this.history = history;
        this.events = events;
        this.properties = properties;
    }

    /** @throws ApiException 404, 403 unless maySeePickups */
    @Transactional(readOnly = true)
    public List<RegularPickup> list(UUID storeId) {
        Store store = stores.require(storeId);
        StorePermissionService.require(rights.rights(currentActor.requirePrincipal(), store).maySeePickups());
        return regular.findByStoreId(storeId).stream().sorted(ORDER).toList();
    }

    /**
     * Replaces the schedule; logs UPDATE_REGULAR_PICKUP_SETTINGS (old / new as text) and tells the team
     * (STORE_REGULAR_PICKUP_CHANGED) when something changed.
     *
     * @throws ApiException 404, 403 unless mayEditPickups, 400 VALIDATION_FAILED / SLOT_COUNT_OUT_OF_RANGE /
     *                      DUPLICATE_PICKUP_DAY_TIME
     */
    @Transactional
    public List<RegularPickup> replace(UUID storeId, List<Entry> entries) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.rights(actor, store).mayEditPickups());
        List<RegularPickup> next = validate(storeId, entries == null ? List.of() : entries,
                properties.getStore().getMaxSlotsPerPickup());
        List<RegularPickup> previous = regular.findByStoreId(storeId).stream().sorted(ORDER).toList();
        String before = describe(previous);
        String after = describe(next);
        if (before.equals(after)) {
            return previous;
        }
        regular.deleteByStoreId(storeId);
        List<RegularPickup> saved = regular.saveAll(next).stream().sorted(ORDER).toList();
        history.record(history.event(StoreLogAction.UPDATE_REGULAR_PICKUP_SETTINGS, store.getId())
                .region(store.getRegionId()).change("regularPickups", before, after).build());
        List<UUID> team = stores.teamIds(storeId).stream().filter(id -> !id.equals(actor.userId())).toList();
        StoreNotifications.send(events, NotificationRequest.to(team, NotificationType.STORE_REGULAR_PICKUP_CHANGED)
                .param("storeId", storeId.toString()).param("storeName", store.getName())
                .link("/stores/" + storeId + "/pickups").icon("calendar")
                .identifier(BellService.identifier(NotificationType.STORE_REGULAR_PICKUP_CHANGED, storeId)).build());
        return saved;
    }

    static List<RegularPickup> validate(UUID storeId, List<Entry> entries, int maxSlots) {
        List<RegularPickup> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry e : entries) {
            if (e == null || e.weekday() == null || e.startTime() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            LocalTime time;
            try {
                time = LocalTime.parse(e.startTime().strip());
            } catch (DateTimeParseException ex) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            if (time.getSecond() != 0 || time.getNano() != 0) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            if (e.slotCount() < 0 || e.slotCount() > maxSlots) {
                throw ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
            }
            String description = e.description() == null || e.description().isBlank() ? null : e.description().strip();
            if (description != null && description.length() > 100) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            if (!seen.add(e.weekday() + "@" + time)) {
                throw ApiException.badRequest(ApiErrorCode.DUPLICATE_PICKUP_DAY_TIME);
            }
            RegularPickup r = new RegularPickup();
            r.setStoreId(storeId);
            r.setWeekday(e.weekday());
            r.setStartTime(time);
            r.setSlotCount(e.slotCount());
            r.setDescription(description);
            result.add(r);
        }
        result.sort(ORDER);
        return result;
    }

    static String describe(List<RegularPickup> list) {
        return String.join("; ", list.stream().sorted(ORDER).map(r -> r.getWeekday() + " " + r.getStartTime() + " ×"
                + r.getSlotCount() + (r.getDescription() == null ? "" : " (" + r.getDescription() + ")")).toList());
    }
}
