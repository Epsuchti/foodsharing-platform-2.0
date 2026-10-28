package network.foodsharing.notification.newsletter.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NewsletterStatus;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.newsletter.domain.Newsletter;
import network.foodsharing.notification.newsletter.domain.NewsletterRecipient;
import network.foodsharing.notification.newsletter.repository.NewsletterRecipientRepository;
import network.foodsharing.notification.newsletter.repository.NewsletterRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code newsletter-send} (every minute): turns up to {@code min(batch-size, mails-per-minute)} pending recipients
 * of queued newsletters into outbox mails (category NEWSLETTER, layout with unsubscribe link); a newsletter is SENT
 * once no recipient is pending. Recipients who left or opted out meanwhile count as failed.
 */
@Component
public class NewsletterSendJob implements RunnableJob {

    static final String NAME = "newsletter-send";

    private final JobRunner jobRunner;
    private final NewsletterRepository newsletters;
    private final NewsletterRecipientRepository recipients;
    private final NewsletterService service;
    private final MailOutboxService outbox;
    private final UserService users;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public NewsletterSendJob(JobRunner jobRunner, NewsletterRepository newsletters,
                             NewsletterRecipientRepository recipients, NewsletterService service,
                             MailOutboxService outbox, UserService users, HistoryService history,
                             AppProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.jobRunner = jobRunner;
        this.newsletters = newsletters;
        this.recipients = recipients;
        this.service = service;
        this.outbox = outbox;
        this.users = users;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.newsletter.send-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "newsletter-send", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    public String schedule() {
        return properties.getNewsletter().getSendCron();
    }

    @Override
    public void runNow() {
        tx.executeWithoutResult(status -> sendBatch());
    }

    /** @return number of mails queued in this run */
    int sendBatch() {
        int budget = Math.max(0, Math.min(properties.getNewsletter().getBatchSize(),
                properties.getNewsletter().getMailsPerMinute()));
        int queued = 0;
        List<Newsletter> due = newsletters.findByStatusIn(List.of(NewsletterStatus.QUEUED, NewsletterStatus.SENDING))
                .stream().sorted(Comparator.comparing(Newsletter::getQueuedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        for (Newsletter newsletter : due) {
            if (newsletter.getStatus() == NewsletterStatus.QUEUED) {
                newsletter.setStatus(NewsletterStatus.SENDING);
            }
            if (budget > 0) {
                for (NewsletterRecipient recipient : recipients.findByNewsletterIdAndStatusOrderByCreatedAtAscIdAsc(
                        newsletter.getId(), NewsletterRecipient.Status.PENDING, PageRequest.of(0, budget))) {
                    Optional<User> user = users.findActive(recipient.getUserId()).filter(User::isNewsletterOptIn);
                    if (user.isEmpty()) {
                        recipient.setStatus(NewsletterRecipient.Status.FAILED);
                        newsletter.setFailedCount(newsletter.getFailedCount() + 1);
                        continue;
                    }
                    recipient.setMailOutboxId(outbox.enqueue(service.draft(newsletter, user.get(),
                            "newsletter-" + newsletter.getId() + "-" + user.get().getId())));
                    recipient.setStatus(NewsletterRecipient.Status.QUEUED);
                    newsletter.setSentCount(newsletter.getSentCount() + 1);
                    budget--;
                    queued++;
                }
            }
            if (recipients.countByNewsletterIdAndStatus(newsletter.getId(), NewsletterRecipient.Status.PENDING) == 0) {
                newsletter.setStatus(NewsletterStatus.SENT);
                newsletter.setSentAt(clock.instant());
                history.record(history.event(NotificationAction.NEWSLETTER_SENT, newsletter.getId())
                        .metadata("sent", newsletter.getSentCount()).metadata("failed", newsletter.getFailedCount())
                        .build());
            }
        }
        return queued;
    }
}
