package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code store-maintenance} (nightly, §6.3): per live store, (1) unconfirmed sign-ups within 48 h →
 * PICKUP_UNCONFIRMED_REMINDER to the managers (one grouped bell per store), (2) pickups tomorrow (store time) without
 * any sign-up → PICKUP_EMPTY_TOMORROW to the managers, (3) managers without a login for 90 days →
 * STORE_MANAGER_INACTIVE to the other managers (once per 30 days and manager). Bells only.
 */
@Component
public class StoreMaintenanceJob implements RunnableJob {

    static final String NAME = "store-maintenance";
    static final Duration UNCONFIRMED_WINDOW = Duration.ofHours(48);
    static final Duration INACTIVE_AFTER = Duration.ofDays(90);
    static final Duration INACTIVE_REPEAT = Duration.ofDays(30);

    private final JobRunner jobRunner;
    private final StoreService stores;
    private final PickupScheduleService schedule;
    private final PickupAutomationQueries pickups;
    private final UserService users;
    private final NotificationService notifications;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public StoreMaintenanceJob(JobRunner jobRunner, StoreService stores, PickupScheduleService schedule,
                               PickupAutomationQueries pickups, UserService users, NotificationService notifications,
                               AppProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.jobRunner = jobRunner;
        this.stores = stores;
        this.schedule = schedule;
        this.pickups = pickups;
        this.users = users;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.jobs.store-maintenance-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "store-maintenance", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    public String schedule() {
        return properties.getJobs().getStoreMaintenanceCron();
    }

    @Override
    public void runNow() {
        Instant now = clock.instant();
        for (Store store : stores.allLiveStores()) {
            tx.executeWithoutResult(status -> maintain(store, now));
        }
    }

    /** @return number of bells sent for the store */
    int maintain(Store store, Instant now) {
        List<UUID> managers = stores.managerIds(store.getId());
        if (managers.isEmpty()) {
            return 0;
        }
        int sent = 0;
        String link = "/stores/" + store.getId() + "/pickups";
        long unconfirmed = pickups.unconfirmedFuture(List.of(store.getId()), now).stream()
                .filter(s -> s.getPickupAt().isBefore(now.plus(UNCONFIRMED_WINDOW))).count();
        if (unconfirmed > 0) {
            String identifier = "PICKUP_UNCONFIRMED_REMINDER-" + store.getId();
            notifications.removeBells(identifier);
            notifications.notify(NotificationRequest.to(managers, NotificationType.PICKUP_UNCONFIRMED_REMINDER)
                    .param("storeId", store.getId().toString()).param("storeName", store.getName())
                    .param("count", String.valueOf(unconfirmed)).link(link).identifier(identifier).build());
            sent++;
        }
        ZoneId zone = schedule.zoneOf(store);
        LocalDate tomorrow = now.atZone(zone).toLocalDate().plusDays(1);
        List<PickupSchedule.Slot> empty = schedule.schedule(store, tomorrow.atStartOfDay(zone).toInstant(),
                tomorrow.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .filter(s -> s.slotCount() > 0 && s.signups().isEmpty()).toList();
        for (PickupSchedule.Slot slot : empty) {
            notifications.notify(NotificationRequest.to(managers, NotificationType.PICKUP_EMPTY_TOMORROW)
                    .param("storeId", store.getId().toString()).param("storeName", store.getName())
                    .param("pickupAt", slot.pickupAt().toString()).link(link)
                    .identifier("PICKUP_EMPTY_TOMORROW-" + store.getId() + "-" + slot.pickupAt()).build());
            sent++;
        }
        Map<UUID, User> byId = users.findAll(managers).stream().collect(Collectors.toMap(User::getId, u -> u));
        for (UUID managerId : managers) {
            User manager = byId.get(managerId);
            Instant lastLogin = manager == null ? null : Optional.ofNullable(manager.getLastLoginAt())
                    .orElse(manager.getCreatedAt());
            if (lastLogin == null || lastLogin.isAfter(now.minus(INACTIVE_AFTER))) {
                continue;
            }
            List<UUID> others = managers.stream().filter(id -> !id.equals(managerId)).toList();
            String identifier = "STORE_MANAGER_INACTIVE-" + store.getId() + "-" + managerId;
            if (others.isEmpty() || notifications.sentSince(identifier, now.minus(INACTIVE_REPEAT))) {
                continue;
            }
            notifications.notify(NotificationRequest.to(others, NotificationType.STORE_MANAGER_INACTIVE)
                    .param("storeId", store.getId().toString()).param("storeName", store.getName())
                    .param("userName", manager.getDisplayName()).link("/stores/" + store.getId() + "/team")
                    .identifier(identifier).build());
            sent++;
        }
        return sent;
    }
}
