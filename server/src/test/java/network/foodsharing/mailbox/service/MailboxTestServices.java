package network.foodsharing.mailbox.service;

import java.time.Clock;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxMemberRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationContext;

/**
 * The real mailbox services on the shared slice's repositories (docs/conventions.md A60: no {@code @MockitoBean}
 * context of their own), wired to the collaborators a test passes in, usually mocks.
 */
final class MailboxTestServices {

    final MailboxAccessService access;
    final MailAttachmentStore attachmentStore;
    final MailboxService mailboxes;
    final MailService mail;
    final IncomingMailProcessor incoming;

    MailboxTestServices(ApplicationContext context, RegionService regions, PermissionService permissions,
                        UploadService uploads, MailOutboxService outbox, RealtimePublisher realtime,
                        NotificationService notifications) {
        MailboxRepository mailboxRepository = context.getBean(MailboxRepository.class);
        MailboxMemberRepository members = context.getBean(MailboxMemberRepository.class);
        MailMessageRepository mails = context.getBean(MailMessageRepository.class);
        MailAttachmentRepository attachments = context.getBean(MailAttachmentRepository.class);
        UserService users = context.getBean(UserService.class);
        HistoryService history = context.getBean(HistoryService.class);
        AppProperties properties = context.getBean(AppProperties.class);
        Clock clock = context.getBean(Clock.class);
        MailHtmlSanitizer sanitizer = new MailHtmlSanitizer();

        access = new MailboxAccessService(mailboxRepository, members, mails, regions, permissions);
        attachmentStore = new MailAttachmentStore(attachments, uploads);
        mailboxes = new MailboxService(mailboxRepository, members, mails, attachments, access, attachmentStore, regions,
                users, permissions, history, properties);
        mail = new MailService(mails, attachments, access, mailboxes, attachmentStore, sanitizer, uploads, outbox,
                realtime, history, properties, clock);
        incoming = new IncomingMailProcessor(mailboxRepository, mails, access, attachmentStore, sanitizer, mail,
                uploads, users, notifications, history, properties, clock);
    }
}
