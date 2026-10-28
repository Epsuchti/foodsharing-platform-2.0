package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.domain.MailFolder;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.upload.service.DiskUploadStorage;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** MB5/A35: one test per .eml fixture (folder, flags, attachments, unknown recipients, bounces, dedupe). */
@JpaSliceTest
@Import({IncomingMailProcessor.class, MailService.class, MailboxService.class, MailboxAccessService.class,
        MailAttachmentStore.class, MailHtmlSanitizer.class, UserService.class, UploadService.class,
        DiskUploadStorage.class})
class IncomingMailProcessorTest {

    @Autowired
    private IncomingMailProcessor processor;
    @Autowired
    private MailboxRepository mailboxes;
    @Autowired
    private MailMessageRepository mails;
    @Autowired
    private MailAttachmentRepository attachments;
    @Autowired
    private UserRepository users;
    @MockitoBean
    private RegionService regions;
    @MockitoBean
    private PermissionService permissions;
    @MockitoBean
    private MailOutboxService outbox;
    @MockitoBean
    private RealtimePublisher realtime;
    @MockitoBean
    private NotificationService notifications;

    private Mailbox zuerich;
    private Mailbox welcome;
    private UUID reader;

    @BeforeEach
    void setUp() {
        reader = users.saveAndFlush(TestUsers.user("in-reader@test.local", Role.AMBASSADOR)).getId();
        zuerich = mailbox("zuerich");
        welcome = mailbox("welcome-zuerich");
        when(regions.adminIds(any())).thenReturn(Set.of(reader));
    }

    private Mailbox mailbox(String emailName) {
        Mailbox mailbox = new Mailbox();
        mailbox.setName(emailName);
        mailbox.setEmailName(emailName);
        mailbox.setOwnerType(MailboxOwnerType.REGION);
        mailbox.setOwnerId(UUID.randomUUID());
        return mailboxes.saveAndFlush(mailbox);
    }

    private static byte[] eml(String name) throws IOException {
        try (InputStream in = IncomingMailProcessorTest.class.getResourceAsStream("/mail/" + name)) {
            return in.readAllBytes();
        }
    }

    private List<MailMessage> inbox(Mailbox mailbox) {
        return mails.findByMailboxId(mailbox.getId()).stream().filter(m -> m.getFolder() == MailFolder.INBOX).toList();
    }

    @Test
    void plainMailLandsUnreadInTheInboxWithBellAndRealtime() throws IOException {
        IncomingMailProcessor.Result result = processor.process(eml("plain.eml"));
        assertThat(result.stored()).isEqualTo(1);
        MailMessage mail = inbox(zuerich).get(0);
        assertThat(mail.getReadAt()).isNull();
        assertThat(mail.getFromName()).isEqualTo("Anna Keller");
        assertThat(mail.getSubject()).isEqualTo("Frage zur Abholung");
        assertThat(mail.getBodyText()).contains("Wann ist die nächste Abholung?");
        assertThat(mail.getMessageId()).isEqualTo("<plain-1@example.ch>");
        assertThat(mail.isBounce()).isFalse();
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().recipientIds()).containsExactly(reader);
        assertThat(bell.getValue().identifier()).isEqualTo("MAILBOX_NEW_MAIL-" + zuerich.getId());
        verify(realtime).toUsers(any(), any());
    }

    @Test
    void retriedDeliveriesAreIgnoredAndBellsAreGroupedPerHour() throws IOException {
        processor.process(eml("plain.eml"));
        assertThat(processor.process(eml("plain.eml")).duplicates()).isEqualTo(1);
        when(notifications.sentSince(any(), any())).thenReturn(true);
        processor.process(eml("umlaut-subject.eml"));
        assertThat(inbox(zuerich)).hasSize(2);
        verify(notifications, times(1)).notify(any());
    }

    @Test
    void htmlIsSanitisedAndInlineImagesPointToTheirAttachment() throws IOException {
        processor.process(eml("html-inline-image.eml"));
        MailMessage mail = inbox(zuerich).get(0);
        List<MailAttachment> files = attachments.findByMailIdOrderByCreatedAtAsc(mail.getId());
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getContentId()).isEqualTo("logo@example.ch");
        assertThat(mail.getBodyHtml()).doesNotContain("script", "tracker", "javascript:")
                .contains("/api/mailboxes/mails/" + mail.getId() + "/attachments/" + files.get(0).getId());
        assertThat(mail.getBodyText()).contains("Hallo");
    }

    @Test
    void attachmentsAreStoredAsUploads() throws IOException {
        processor.process(eml("two-attachments.eml"));
        MailMessage mail = inbox(zuerich).get(0);
        assertThat(mail.getAttachmentCount()).isEqualTo(2);
        assertThat(attachments.findByMailIdOrderByCreatedAtAsc(mail.getId())).extracting(MailAttachment::getFileName)
                .containsExactly("plan.pdf", "liste.txt");
        assertThat(mail.getBodyText()).isEqualTo("Anbei zwei Dateien.");
    }

    @Test
    void everyKnownMailboxGetsACopyUnknownRecipientsAreDropped() throws IOException {
        IncomingMailProcessor.Result result = processor.process(eml("multi-recipient.eml"));
        assertThat(result.stored()).isEqualTo(2);
        assertThat(result.unknownRecipients()).containsExactly("gibtsnicht@local.test");
        assertThat(inbox(zuerich)).hasSize(1);
        assertThat(inbox(welcome)).hasSize(1);
        assertThat(inbox(welcome).get(0).getCcJson()).contains("extern@example.com");
    }

    @Test
    void mailForNoKnownMailboxIsDropped() throws IOException {
        byte[] raw = new String(eml("plain.eml")).replace("zuerich@local.test", "nobody@local.test").getBytes();
        assertThat(processor.process(raw).stored()).isZero();
        verify(notifications, never()).notify(any());
    }

    @Test
    void deliveryReportsFlagTheSentMailAndTheBouncedUserAddress() throws IOException {
        User bounced = users.saveAndFlush(TestUsers.user("bounced-user@example.ch", Role.FOODSAVER));
        MailMessage sent = new MailMessage();
        sent.setMailboxId(zuerich.getId());
        sent.setFolder(MailFolder.SENT);
        sent.setMessageId("<seed-sent-026@local.test>");
        sent.setFromAddress("zuerich@local.test");
        sent.setToJson("[]");
        sent.setSubject("Re: Abholung");
        sent.setReceivedAt(java.time.Instant.parse("2026-11-01T10:00:00Z"));
        sent = mails.saveAndFlush(sent);

        IncomingMailProcessor.Result result = processor.process(eml("bounce-dsn.eml"));
        assertThat(result.bounce()).isTrue();
        MailMessage dsn = inbox(zuerich).get(0);
        assertThat(dsn.isBounce()).isTrue();
        assertThat(dsn.getBouncedMessageId()).isEqualTo("<seed-sent-026@local.test>");
        assertThat(mails.findById(sent.getId()).orElseThrow().isBounce()).isTrue();
        assertThat(users.findById(bounced.getId()).orElseThrow().getEmailBouncedAt()).isNotNull();
    }

    @Test
    void autoRepliesAreFlaggedButDoNotMarkSentMailsBounced() throws IOException {
        processor.process(eml("auto-reply.eml"));
        MailMessage mail = inbox(zuerich).get(0);
        assertThat(mail.isBounce()).isTrue();
        assertThat(mail.getBouncedMessageId()).isNull();
        assertThat(mail.getInReplyTo()).isEqualTo("<seed-sent-027@local.test>");
    }

    @Test
    void encodedSubjectsAndCharsetsAreDecoded() throws IOException {
        processor.process(eml("umlaut-subject.eml"));
        MailMessage mail = inbox(zuerich).get(0);
        assertThat(mail.getSubject()).isEqualTo("Grüsse aus der Bäckerei");
        assertThat(mail.getFromName()).isEqualTo("Bäckerei Güt");
        assertThat(mail.getBodyText()).contains("Schöne Grüsse");
    }

    @Test
    void nestedAlternativeKeepsTextHtmlAndTheAttachment() throws IOException {
        processor.process(eml("nested-alternative.eml"));
        MailMessage mail = inbox(zuerich).get(0);
        assertThat(mail.getBodyText()).contains("Einladung zum Fest");
        assertThat(mail.getBodyHtml()).isEqualTo("<p>Einladung zum <b>Fest</b></p>");
        assertThat(attachments.findByMailIdOrderByCreatedAtAsc(mail.getId())).extracting(MailAttachment::getFileName)
                .containsExactly("fest.ics");
    }

    @Test
    void garbageStoresNothing() {
        assertThat(processor.process("no headers at all".getBytes()).stored()).isZero();
    }
}
