package network.foodsharing.notification.mail.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers due outbox mails every 10 s (off in the test profile; tests call {@link MailOutboxService#processDue}). */
@Component
public class MailOutboxJob {

    private final MailOutboxService outbox;

    public MailOutboxJob(MailOutboxService outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox-interval-ms:10000}", initialDelayString = "${app.mail.outbox-interval-ms:10000}")
    public void run() {
        outbox.processDue();
    }
}
