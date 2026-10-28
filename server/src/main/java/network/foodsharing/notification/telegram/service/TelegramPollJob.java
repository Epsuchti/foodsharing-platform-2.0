package network.foodsharing.notification.telegram.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code telegram-poll} (A28): fetches Bot API updates after the stored offset and hands them to the
 * {@link TelegramUpdateProcessor}. Runs only in live mode without a webhook (Telegram refuses getUpdates while a
 * webhook is set) and while the TELEGRAM breaker is closed.
 */
@Component
public class TelegramPollJob implements RunnableJob {

    private final JobRunner jobRunner;
    private final TelegramClient client;
    private final TelegramUpdateProcessor processor;
    private final IntegrationRegistry integrations;
    private final AppProperties properties;

    public TelegramPollJob(JobRunner jobRunner, TelegramClient client, TelegramUpdateProcessor processor,
                           IntegrationRegistry integrations, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.client = client;
        this.processor = processor;
        this.integrations = integrations;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.telegram.poll-interval}")
    @SchedulerLock(name = "telegram-poll", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "telegram-poll";
    }

    @Override
    public String schedule() {
        return "every " + properties.getTelegram().getPollInterval();
    }

    @Override
    public boolean recordEveryRun() {
        return false;
    }

    @Override
    public void runNow() {
        String secret = properties.getTelegram().getWebhookSecret();
        if (integrations.mode(Integration.TELEGRAM) != IntegrationRegistry.Mode.LIVE
                || (secret != null && !secret.isBlank()) || integrations.shouldSkip(Integration.TELEGRAM)
                || properties.getTelegram().getBotToken() == null || properties.getTelegram().getBotToken().isBlank()) {
            return;
        }
        processor.processAll(client.getUpdates(processor.lastUpdateId() + 1));
    }
}
