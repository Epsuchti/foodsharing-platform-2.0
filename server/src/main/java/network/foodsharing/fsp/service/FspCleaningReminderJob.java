package network.foodsharing.fsp.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspAction;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly cleaning reminders (decision 155; dev name {@code fsp-cleaning-reminder}), with the timing and the user switch
 * of the pickup reminders (PU9): sign-ups whose shift lies in {@code (now + hours − 1 h, now + hours]}, not yet
 * reminded, of active users with reminders enabled, get a bell + mail FSP_CLEANING_REMINDER; {@code reminded_at} makes
 * it idempotent. History FspAction.CLEANING_REMINDER_SENT (subject = Fairteiler, actor AUTOMATION).
 */
@Component
public class FspCleaningReminderJob implements RunnableJob {

    static final String NAME = "fsp-cleaning-reminder";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JobRunner jobRunner;
    private final FspCleaningSignupRepository signups;
    private final FoodSharePointRepository fsps;
    private final UserService users;
    private final HistoryService history;
    private final NotificationService notifications;
    private final AppProperties properties;
    private final Clock clock;

    public FspCleaningReminderJob(JobRunner jobRunner, FspCleaningSignupRepository signups,
                                  FoodSharePointRepository fsps, UserService users, HistoryService history,
                                  NotificationService notifications, AppProperties properties, Clock clock) {
        this.jobRunner = jobRunner;
        this.signups = signups;
        this.fsps = fsps;
        this.users = users;
        this.history = history;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.pickup.reminder-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "fsp-cleaning-reminder", lockAtLeastFor = "PT5S")
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
        List<FspCleaningSignup> due = signups.findToRemind(to.minus(Duration.ofHours(1)), to);
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, FoodSharePoint> fspById = fsps.findByIdInAndDeletedAtIsNullOrderByNameAscIdAsc(due.stream()
                .map(FspCleaningSignup::getFspId).distinct().toList()).stream()
                .filter(FoodSharePoint::isCleaningEnabled)
                .collect(Collectors.toMap(FoodSharePoint::getId, Function.identity()));
        Map<UUID, User> userById = users.findAll(due.stream().map(FspCleaningSignup::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        int sent = 0;
        for (FspCleaningSignup signup : due) {
            FoodSharePoint fsp = fspById.get(signup.getFspId());
            User user = userById.get(signup.getUserId());
            if (fsp == null || user == null || !user.isActive() || !user.isPickupRemindersEnabled()) {
                continue;
            }
            // only sign-ups that are actually reminded are marked, so turning reminders back on still reminds
            signup.setRemindedAt(now);
            notifications.notify(NotificationRequest.to(List.of(user.getId()), NotificationType.FSP_CLEANING_REMINDER)
                    .param("fspId", fsp.getId().toString()).param("name", fsp.getName())
                    .param("shiftAt", signup.getShiftAt().toString())
                    .param("when", WHEN.format(signup.getShiftAt().atZone(ZoneId.of(fsp.getTimeZone()))))
                    .link(FspCleaningScheduleService.link(fsp.getId())).icon("alarm-clock")
                    .mail("fsp/cleaning-reminder")
                    .identifier(BellService.identifier(NotificationType.FSP_CLEANING_REMINDER, fsp.getId(),
                            signup.getShiftAt().toString())).build());
            history.record(history.event(FspAction.CLEANING_REMINDER_SENT, fsp.getId())
                    .secondarySubject(user.getId()).region(fsp.getRegionId())
                    .metadata("shiftAt", signup.getShiftAt().toString()).actorAutomation(NAME).build());
            sent++;
        }
        return sent;
    }
}
