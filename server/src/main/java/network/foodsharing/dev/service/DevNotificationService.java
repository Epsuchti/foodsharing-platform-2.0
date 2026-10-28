package network.foodsharing.dev.service;

import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.CapturedMessages;
import network.foodsharing.notification.telegram.service.TelegramClient;
import network.foodsharing.notification.telegram.service.TelegramUpdateProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Dev API for M2 tests: persisted notifications, running jobs by name, the iCal fixture (local/e2e only). */
@Service
@Profile({"local", "e2e"})
public class DevNotificationService {

    /** A bell with its parameters. */
    public record DevBell(Bell bell, Map<String, String> params) {
    }

    private final BellService bells;
    private final List<RunnableJob> jobs;
    private final JobRunner jobRunner;
    private final CapturedMessages captured;
    private final TelegramUpdateProcessor telegram;
    private final IntegrationRegistry integrations;

    public DevNotificationService(BellService bells, List<RunnableJob> jobs, JobRunner jobRunner,
                                  CapturedMessages captured, TelegramUpdateProcessor telegram,
                                  IntegrationRegistry integrations) {
        this.integrations = integrations;
        this.telegram = telegram;
        this.jobRunner = jobRunner;
        this.captured = captured;
        this.bells = bells;
        this.jobs = jobs;
    }

    /** Bells (incl. deleted ones) newest first, optionally filtered by user and type name. */
    public List<DevBell> notifications(UUID userId, String type) {
        NotificationType notificationType = null;
        if (type != null && !type.isBlank()) {
            try {
                notificationType = NotificationType.valueOf(type);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        return bells.search(userId, notificationType).stream().map(bell -> new DevBell(bell, bells.params(bell)))
                .toList();
    }

    /** @throws ApiException 404 DEV_JOB_NOT_FOUND */
    public void runJob(String name) {
        RunnableJob job = jobs.stream().filter(candidate -> candidate.jobName().equals(name)).findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.DEV_JOB_NOT_FOUND));
        jobRunner.run(job, JobRun.Trigger.MANUAL, null);
    }

    /** Push / Telegram messages captured by the log-only clients (A23). */
    public List<CapturedMessages.Captured> captured(UUID userId, String type, NotificationChannelType channel) {
        return captured.list(userId, type, channel);
    }

    public void clearCaptured() {
        captured.clear();
    }

    public List<String> jobNames() {
        return jobs.stream().map(RunnableJob::jobName).sorted().toList();
    }

    /** Static calendar used by the guide iCal import e2e tests ({@code dev/ical-fixture.ics}). */
    public String icalFixture() {
        try (var in = new ClassPathResource("dev/ical-fixture.ics").getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("dev/ical-fixture.ics missing", e);
        }
    }

    /** {@code POST /api/dev/telegram/incoming}: an update as if the bot had received it (title = group chat). */
    public void simulateTelegram(String chatId, String text, String title) {
        telegram.process(new TelegramClient.Update(telegram.nextSyntheticUpdateId(), chatId,
                title == null || title.isBlank() ? "private" : "group", title, text));
    }

    /** {@code POST /api/dev/integrations/{integration}/fail}: opens the breaker (ORGA gets INTEGRATION_DOWN). */
    public void simulateIntegrationFailure(Integration integration) {
        integrations.simulateOutage(integration);
    }
}
