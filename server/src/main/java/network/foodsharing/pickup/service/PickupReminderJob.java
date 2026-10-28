package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.pickup.domain.PickupAction;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly pickup reminders (PU9; dev name {@code pickup-reminder}): sign-ups whose pickup lies in
 * {@code (now + hours − 1 h, now + hours]}, not yet reminded, of active users with reminders enabled, get a bell + mail
 * PICKUP_REMINDER; {@code reminded_at} makes it idempotent. History PickupAction.REMINDER_SENT (subject = store,
 * actor AUTOMATION).
 */
@Component
public class PickupReminderJob implements RunnableJob {

    private final JobRunner jobRunner;

    static final String NAME = "pickup-reminder";

    private final PickupSignupRepository signups;
    private final StoreService stores;
    private final UserService users;
    private final PickupScheduleService schedule;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    public PickupReminderJob(JobRunner jobRunner, PickupSignupRepository signups, StoreService stores, UserService users,
                             PickupScheduleService schedule, HistoryService history, ApplicationEventPublisher events,
                             AppProperties properties, Clock clock) {
        this.jobRunner = jobRunner;
        this.signups = signups;
        this.stores = stores;
        this.users = users;
        this.schedule = schedule;
        this.history = history;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.pickup.reminder-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "pickup-reminder", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    @Transactional
    public void runNow() {
        remind();
    }

    /** @return number of reminders sent */
    @Transactional
    public int remind() {
        Instant now = clock.instant();
        Instant to = now.plus(Duration.ofHours(properties.getPickup().getReminderHoursBefore()));
        List<PickupSignup> due = signups.findToRemind(to.minus(Duration.ofHours(1)), to);
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, Store> storeById = stores.liveStores(due.stream().map(PickupSignup::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<UUID, User> userById = users.findAll(due.stream().map(PickupSignup::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        int sent = 0;
        for (PickupSignup signup : due) {
            signup.setRemindedAt(now);
            Store store = storeById.get(signup.getStoreId());
            User user = userById.get(signup.getUserId());
            if (store == null || user == null || !user.isActive() || !user.isPickupRemindersEnabled()) {
                continue;
            }
            StoreNotifications.send(events, NotificationRequest.to(List.of(user.getId()),
                    NotificationType.PICKUP_REMINDER).param("storeId", store.getId().toString())
                    .param("storeName", store.getName()).param("pickupAt", signup.getPickupAt().toString())
                    .param("when", PickupFormat.when(signup.getPickupAt(), schedule.zoneOf(store)))
                    .link("/stores/" + store.getId() + "/pickups").icon("alarm-clock").mail("pickup/reminder")
                    .identifier(BellService.identifier(NotificationType.PICKUP_REMINDER, store.getId(),
                            signup.getPickupAt().toString())).build());
            history.record(history.event(PickupAction.REMINDER_SENT, store.getId()).secondarySubject(user.getId())
                    .region(store.getRegionId()).metadata("pickupAt", signup.getPickupAt().toString())
                    .actorAutomation(NAME).build());
            sent++;
        }
        return sent;
    }
}
