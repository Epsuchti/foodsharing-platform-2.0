package network.foodsharing.notification.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.integration.IntegrationRegistry.IntegrationDownEvent;
import network.foodsharing.common.jobs.JobRunner.JobFailedEvent;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Operational bells to every active ORGA user (A25/A26): INTEGRATION_DOWN when a breaker opens (once per outage, with
 * mail) and AUTOMATION_RUN_FAILED when a job failed twice in a row. Runs after the publishing transaction (or right
 * away without one) in its own transaction; failures are logged only.
 */
@Component
public class OrgaAlerts {

    private static final Logger log = LoggerFactory.getLogger(OrgaAlerts.class);

    private final NotificationService notifications;
    private final UserService users;
    private final TransactionTemplate tx;

    public OrgaAlerts(NotificationService notifications, UserService users, PlatformTransactionManager transactions) {
        this.notifications = notifications;
        this.users = users;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onIntegrationDown(IntegrationDownEvent event) {
        send(NotificationRequest.to(List.of(), NotificationType.INTEGRATION_DOWN)
                .param("integration", event.integration().name())
                .param("message", event.error())
                .link("/admin/integrations")
                .identifier("INTEGRATION_DOWN-" + event.integration().name())
                .mail("notification/INTEGRATION_DOWN")
                .build());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onJobFailed(JobFailedEvent event) {
        send(NotificationRequest.to(List.of(), NotificationType.AUTOMATION_RUN_FAILED)
                .param("job", event.jobName())
                .param("message", event.message())
                .link("/admin/jobs")
                .identifier("AUTOMATION_RUN_FAILED-" + event.jobName())
                .build());
    }

    private List<UUID> orga() {
        return users.findActiveByRole(Role.ORGA).stream().map(User::getId).toList();
    }

    private void send(NotificationRequest template) {
        try {
            tx.executeWithoutResult(status -> {
                List<UUID> recipients = orga();
                if (!recipients.isEmpty()) {
                    notifications.notify(new NotificationRequest(java.util.Set.copyOf(recipients), template.type(),
                            template.params(), template.link(), template.mailTemplate(), template.bellOnly(),
                            template.identifier(), template.icon()));
                }
            });
        } catch (RuntimeException ex) {
            log.warn("ORGA alert {} failed: {}", template.type(), ex.toString());
        }
    }
}
