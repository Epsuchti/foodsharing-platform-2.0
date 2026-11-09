package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.service.MagicLinkService;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.seed.SeedPersonas;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.MailTransport;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.RecordingMailTransport;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Decision 173: the staging recipient policy, in deliver and sendTest; prod ("all") sends everything. */
class MailRecipientPolicyTest {

    private final AppProperties properties = new AppProperties();
    private final UserService userService = mock(UserService.class);
    private final MailOutboxRepository repository = mock(MailOutboxRepository.class);
    private final HistoryService history = mock(HistoryService.class);
    private final RecordingMailTransport transport = new RecordingMailTransport();
    private final MutableClock clock = new FixedClockConfig().clock();
    private MailRecipientPolicy policy;
    private MailDeliveryService delivery;

    @BeforeEach
    void setUp() {
        properties.getMail().setRecipientPolicy(MailRecipientPolicy.STAGING);
        properties.getMail().setAllowedRecipients(List.of("Eric@Example.org", " "));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userService", userService);
        beans.addBean("transport", transport);
        when(userService.findByEmail(any())).thenReturn(Optional.empty());
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        policy = new MailRecipientPolicy(properties, beans.getBeanProvider(UserService.class));
        IntegrationRegistry integrations = mock(IntegrationRegistry.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(integrations).run(any(), any(), any());
        delivery = new MailDeliveryService(repository, beans.getBeanProvider(MailTransport.class), history,
                properties, clock, integrations, mock(UploadService.class), policy);
    }

    @Test
    void allPolicySendsEverything() {
        properties.getMail().setRecipientPolicy(MailRecipientPolicy.ALL);
        assertThat(policy.allows("orga@local", null)).isTrue();
        assertThat(policy.allows("someone@example.org", null)).isTrue();
    }

    @Test
    void stagingAllowsRealTestersAuthMailsAndTheAllowList() {
        User tester = user(UUID.randomUUID(), "tester@example.org", true);
        when(userService.findByEmail("tester@example.org")).thenReturn(Optional.of(tester));

        assertThat(policy.allows("Tester@Example.org", null)).isTrue();
        assertThat(policy.allows("eric@example.org", null)).as("allow list, case-insensitive").isTrue();
        assertThat(policy.allows("newcomer@example.org", MailDraft.AUTH)).as("magic link / sign-up").isTrue();
        assertThat(policy.allows("newcomer@example.org", null)).as("not a user").isFalse();
        assertThat(policy.allows("newcomer@example.org", "MAILBOX")).isFalse();
        assertThat(policy.allows(null, MailDraft.AUTH)).isFalse();
    }

    @Test
    void stagingSuppressesSeedInactiveAndReservedAddresses() {
        User seed = user(SeedPersonas.seedUserId(14), "orga@example.org", true);
        User inactive = user(UUID.randomUUID(), "gone@example.org", false);
        when(userService.findByEmail("orga@example.org")).thenReturn(Optional.of(seed));
        when(userService.findByEmail("gone@example.org")).thenReturn(Optional.of(inactive));

        assertThat(policy.allows("orga@example.org", null)).as("seed user").isFalse();
        assertThat(policy.allows("gone@example.org", null)).as("inactive").isFalse();
        assertThat(policy.allows("orga@local", MailDraft.AUTH)).as("seed domain, even auth").isFalse();
        assertThat(policy.allows("outbox-1@local.test", null)).isFalse();
        assertThat(policy.allows("a@b.invalid", MailDraft.AUTH)).isFalse();
        assertThat(policy.allows("noslash", MailDraft.AUTH)).isFalse();
    }

    @Test
    void authCategoryMatchesTheAuthMails() {
        // the magic link (login and sign-up) and the email change mails are queued with category AUTH
        assertThat(new MailDraft("x@example.org", MagicLinkService.MAIL_TEMPLATE, Language.DE, null).category())
                .isEqualTo(MailDraft.AUTH);
    }

    @Test
    void deliverMarksASuppressedMailSentWithoutSendingIt() {
        MailOutboxMessage message = message("orga@local", null);
        delivery.deliver(message.getId());

        assertThat(transport.sent()).isEmpty();
        assertThat(message.getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(message.getSentAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        assertThat(message.getLastError()).isEqualTo("suppressed (staging recipient policy)");
        assertThat(message.getAttempts()).isZero();
    }

    @Test
    void deliverSendsAllowedMails() {
        MailOutboxMessage message = message("newcomer@example.org", MailDraft.AUTH);
        delivery.deliver(message.getId());

        assertThat(transport.sent()).singleElement().satisfies(mail -> assertThat(mail.to())
                .isEqualTo("newcomer@example.org"));
        assertThat(message.getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(message.getLastError()).isNull();
    }

    @Test
    void sendTestRespectsThePolicy() {
        delivery.sendTest("orga@local");
        assertThat(transport.sent()).isEmpty();

        delivery.sendTest("eric@example.org");
        assertThat(transport.sent()).singleElement().satisfies(mail -> assertThat(mail.to())
                .isEqualTo("eric@example.org"));
    }

    private MailOutboxMessage message(String to, String category) {
        MailOutboxMessage message = new MailOutboxMessage();
        message.setId(UUID.randomUUID());
        message.setToAddress(to);
        message.setSubject("Subject");
        message.setTextBody("text");
        message.setLanguage(Language.DE);
        message.setTemplate("magic-link");
        message.setCategory(category);
        message.setNextAttemptAt(FixedClockConfig.SEED_NOW);
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        return message;
    }

    private static User user(UUID id, String email, boolean active) {
        User user = TestUsers.user(email);
        user.setId(id);
        user.setActive(active);
        return user;
    }
}
