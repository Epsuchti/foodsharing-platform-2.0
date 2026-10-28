package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.mailbox.domain.MailFolder;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.notification.mail.service.DirectMail;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** A35/MB2–MB4: compose validation, recipient limit, rate limit, reply headers, folder and delete semantics. */
@JpaSliceTest
@Import({MailService.class, MailboxService.class, MailboxAccessService.class, MailAttachmentStore.class,
        MailHtmlSanitizer.class, UserService.class})
class MailServiceTest {

    @Autowired
    private MailService service;
    @Autowired
    private MailboxService mailboxes;
    @Autowired
    private MailMessageRepository mails;
    @Autowired
    private UserRepository users;
    @Autowired
    private AppProperties properties;
    @Autowired
    private MutableClock clock;
    @MockitoBean
    private RegionService regions;
    @MockitoBean
    private PermissionService permissions;
    @MockitoBean
    private UploadService uploads;
    @MockitoBean
    private MailOutboxService outbox;
    @MockitoBean
    private RealtimePublisher realtime;

    private SessionPrincipal me;
    private Mailbox mailbox;
    private int originalLimit;

    @BeforeEach
    void setUp() {
        User owner = users.saveAndFlush(TestUsers.user("mail-owner@test.local", Role.AMBASSADOR));
        me = TestUsers.principal(owner);
        when(permissions.isOrga(any())).thenReturn(true);
        mailbox = mailboxes.createForUser(me, owner.getId(), "owner", "Owner");
        originalLimit = properties.getMailbox().getMaxMailsPerHourPerUser();
        clock.fixAt(Instant.parse("2026-11-02T09:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        properties.getMailbox().setMaxMailsPerHourPerUser(originalLimit);
        properties.getMailbox().setDomain("local.test");
        clock.release();
    }

    private MailService.Compose compose(List<String> to) {
        return new MailService.Compose(to, null, null, "Betreff", "<p>Hallo</p>", null, null, null);
    }

    private static void expectCode(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void recipientsAreValidatedAndLimited() {
        expectCode(() -> service.send(me, mailbox.getId(), compose(List.of())), ApiErrorCode.INVALID_RECIPIENT);
        expectCode(() -> service.send(me, mailbox.getId(), compose(List.of("kein empfänger"))),
                ApiErrorCode.INVALID_RECIPIENT);
        expectCode(() -> service.send(me, mailbox.getId(), compose(List.of("a@b.ch, c@d.ch"))),
                ApiErrorCode.INVALID_RECIPIENT);
        List<String> many = new ArrayList<>(IntStream.range(0, 101).mapToObj(i -> "r" + i + "@example.ch").toList());
        expectCode(() -> service.send(me, mailbox.getId(), compose(many)), ApiErrorCode.TOO_MANY_RECIPIENTS);
        many.remove(100);
        many.add("R0@example.ch");
        assertThat(service.send(me, mailbox.getId(), compose(many)).getFolder()).isEqualTo(MailFolder.SENT);
        verify(outbox, times(100)).enqueueDirect(any());
    }

    @Test
    void composeNeedsAConfiguredDomain() {
        properties.getMailbox().setDomain("");
        assertThatThrownBy(() -> service.send(me, mailbox.getId(), compose(List.of("a@example.ch"))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.MAILBOX_DOMAIN_NOT_CONFIGURED);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void sendingIsRateLimitedPerUserAndHour() {
        properties.getMailbox().setMaxMailsPerHourPerUser(2);
        service.send(me, mailbox.getId(), compose(List.of("a@example.ch")));
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(30)));
        service.send(me, mailbox.getId(), compose(List.of("a@example.ch")));
        expectCode(() -> service.send(me, mailbox.getId(), compose(List.of("a@example.ch"))),
                ApiErrorCode.MAILBOX_RATE_LIMITED);
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(31)));
        assertThat(service.send(me, mailbox.getId(), compose(List.of("a@example.ch")))).isNotNull();
    }

    @Test
    void sentMailIsSanitisedAndSentFromTheMailboxAddress() {
        MailMessage sent = service.send(me, mailbox.getId(), new MailService.Compose(List.of("Anna <anna@example.ch>"),
                List.of("cc@example.ch"), List.of("bcc@example.ch"), "Hi", "<p onclick=\"x\">Hallo<script>x</script></p>",
                null, null, null));
        assertThat(sent.getBodyHtml()).isEqualTo("<p>Hallo</p>");
        assertThat(sent.getBodyText()).isEqualTo("Hallo");
        assertThat(sent.getMessageId()).endsWith("@local.test>");
        assertThat(sent.getToJson()).contains("anna@example.ch").doesNotContain("bcc@");
        ArgumentCaptor<DirectMail> captor = ArgumentCaptor.forClass(DirectMail.class);
        verify(outbox, times(3)).enqueueDirect(captor.capture());
        assertThat(captor.getAllValues()).extracting(DirectMail::to)
                .containsExactly("anna@example.ch", "cc@example.ch", "bcc@example.ch");
        assertThat(captor.getValue().fromAddress()).isEqualTo("owner@local.test");
        assertThat(captor.getValue().headers()).containsEntry("Message-ID", sent.getMessageId());
        verify(realtime, atLeastOnce()).toUsers(any(), any());
    }

    @Test
    void replySetsAnsweredAndThreadingHeaders() {
        MailMessage incoming = incoming("<orig-1@example.ch>");
        MailMessage reply = service.send(me, mailbox.getId(), new MailService.Compose(List.of("a@example.ch"), null,
                null, "Re: x", "<p>ok</p>", null, incoming.getId(), null));
        assertThat(reply.getInReplyTo()).isEqualTo("<orig-1@example.ch>");
        assertThat(mails.findById(incoming.getId()).orElseThrow().getAnsweredAt()).isNotNull();
        ArgumentCaptor<DirectMail> captor = ArgumentCaptor.forClass(DirectMail.class);
        verify(outbox).enqueueDirect(captor.capture());
        assertThat(captor.getValue().headers()).containsEntry("In-Reply-To", "<orig-1@example.ch>")
                .containsEntry("References", "<orig-1@example.ch>");
    }

    @Test
    void readMoveAndDeleteFollowTheFolderRules() {
        MailMessage mail = incoming("<orig-2@example.ch>");
        assertThat(service.unreadCounts(List.of(mailbox.getId()))).containsEntry(mailbox.getId(), 1);
        assertThat(service.open(me, mail.getId(), true).getReadAt()).isNull();
        assertThat(service.open(me, mail.getId(), false).getReadAt()).isNotNull();
        assertThat(service.totalUnread(me)).isZero();
        service.update(me, mail.getId(), false, null);
        assertThat(service.totalUnread(me)).isEqualTo(1);
        service.delete(me, mail.getId());
        MailMessage trashed = mails.findById(mail.getId()).orElseThrow();
        assertThat(trashed.getFolder()).isEqualTo(MailFolder.TRASH);
        assertThat(trashed.getPreviousFolder()).isEqualTo(MailFolder.INBOX);
        assertThat(service.totalUnread(me)).isZero();
        service.delete(me, mail.getId());
        assertThat(mails.findById(mail.getId())).isEmpty();
    }

    @Test
    void strangersCannotReadTheMailbox() {
        User other = users.saveAndFlush(TestUsers.user("mail-other@test.local", Role.AMBASSADOR));
        MailMessage mail = incoming("<orig-3@example.ch>");
        expectCode(() -> service.open(TestUsers.principal(other), mail.getId(), false), ApiErrorCode.ACCESS_DENIED);
        expectCode(() -> service.page(TestUsers.principal(other), mailbox.getId(), MailFolder.INBOX, 0, 20, null),
                ApiErrorCode.ACCESS_DENIED);
        assertThat(service.page(me, mailbox.getId(), MailFolder.INBOX, 0, 20, "ORIG").getTotalElements()).isEqualTo(1);
    }

    private MailMessage incoming(String messageId) {
        MailMessage mail = new MailMessage();
        mail.setMailboxId(mailbox.getId());
        mail.setFolder(MailFolder.INBOX);
        mail.setMessageId(messageId);
        mail.setFromAddress("sender@example.ch");
        mail.setToJson("[{\"address\":\"owner@local.test\"}]");
        mail.setSubject("Frage " + messageId);
        mail.setBodyText("Text");
        mail.setReceivedAt(clock.instant());
        return mails.saveAndFlush(mail);
    }
}
