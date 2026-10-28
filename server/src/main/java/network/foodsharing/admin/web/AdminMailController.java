package network.foodsharing.admin.web;

import java.util.UUID;
import network.foodsharing.api.AdminMailApi;
import network.foodsharing.api.model.MailOutboxItem;
import network.foodsharing.api.model.MailOutboxPage;
import network.foodsharing.api.model.MailOutboxStatus;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Mail outbox administration (ORGA, N6): list with status/search filter and retry. */
@RestController
public class AdminMailController implements AdminMailApi {

    private static final int MAX_SIZE = 100;

    private final MailOutboxService mails;
    private final CurrentActorService currentActor;

    public AdminMailController(MailOutboxService mails, CurrentActorService currentActor) {
        this.mails = mails;
        this.currentActor = currentActor;
    }

    @Override
    public ResponseEntity<MailOutboxPage> listMailOutbox(MailOutboxStatus status, String search, Integer page,
                                                         Integer size) {
        currentActor.requireRole(Role.ORGA);
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 25 : Math.min(size, MAX_SIZE);
        Page<MailOutboxMessage> result = mails.search(status == null ? null : MailStatus.valueOf(status.getValue()),
                search, p, s);
        return ResponseEntity.ok(new MailOutboxPage().items(result.getContent().stream().map(this::toApi).toList())
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<MailOutboxItem> retryMail(UUID mailId) {
        currentActor.requireRole(Role.ORGA);
        return ResponseEntity.ok(toApi(mails.retry(mailId)));
    }

    private MailOutboxItem toApi(MailOutboxMessage m) {
        return new MailOutboxItem().id(m.getId()).to(m.getToAddress()).subject(m.getSubject())
                .template(m.getTemplate()).status(MailOutboxStatus.fromValue(m.getStatus().name()))
                .attempts(m.getAttempts())
                .nextAttemptAt(m.getStatus() == MailStatus.PENDING ? TimeMapper.toOffset(m.getNextAttemptAt()) : null)
                .lastError(m.getLastError())
                .sentAt(m.getSentAt() == null ? null : TimeMapper.toOffset(m.getSentAt()))
                .createdAt(TimeMapper.toOffset(m.getCreatedAt()));
    }
}
