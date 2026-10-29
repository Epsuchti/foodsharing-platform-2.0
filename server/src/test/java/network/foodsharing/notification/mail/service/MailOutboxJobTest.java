package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.MailTransport;
import network.foodsharing.notification.mail.domain.MailAction;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.RecordingMailTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;

/** Outbox delivery: success, retries with exponential backoff, permanent failure after max attempts. */
class MailOutboxJobTest {

    private final MailOutboxRepository repository = mock(MailOutboxRepository.class);
    private final HistoryService history = mock(HistoryService.class);
    private final RecordingMailTransport transport = new RecordingMailTransport();
    private final MutableClock clock = new FixedClockConfig().clock();
    private final AppProperties properties = new AppProperties();
    private MailDeliveryService delivery;
    private MailOutboxMessage message;

    @BeforeEach
    void setUp() {
        properties.getMail().setFrom("noreply@foodsharing.test");
        properties.getMail().setOutboxMaxAttempts(3);
        properties.getMail().setOutboxBackoffBaseMinutes(1);
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("transport", transport);
        delivery = new MailDeliveryService(repository, beans.getBeanProvider(MailTransport.class), history,
                properties, clock, mock(IntegrationRegistry.class), mock(network.foodsharing.upload.service.UploadService.class));

        message = new MailOutboxMessage();
        message.setId(UUID.randomUUID());
        message.setToAddress("to@example.org");
        message.setSubject("Subject");
        message.setTextBody("text");
        message.setHtmlBody("<p>html</p>");
        message.setLanguage(Language.DE);
        message.setTemplate("magic-link");
        message.setNextAttemptAt(FixedClockConfig.SEED_NOW);
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
    }

    @Test
    void sendsAndMarksSent() {
        delivery.deliver(message.getId());

        assertThat(transport.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo("to@example.org");
            assertThat(mail.from()).isEqualTo("noreply@foodsharing.test");
            assertThat(mail.htmlBody()).isEqualTo("<p>html</p>");
        });
        assertThat(message.getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(message.getSentAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        assertThat(message.getAttempts()).isEqualTo(1);
        verify(history).record(argThat(event -> event.action() == MailAction.SENT));

        delivery.deliver(message.getId());
        assertThat(transport.sent()).hasSize(1);
    }

    @Test
    void regionSenderMailsUseTheRegionDisplayNameWithTheConfiguredAddress() throws Exception {
        message.setFromName("foodsharing Stadt Zürich");
        delivery.deliver(message.getId());
        assertThat(transport.sent()).singleElement().satisfies(mail ->
                assertThat(new jakarta.mail.internet.InternetAddress(mail.from()).getAddress())
                        .isEqualTo("noreply@foodsharing.test"));
        assertThat(new jakarta.mail.internet.InternetAddress(delivery.from(message)).getPersonal())
                .isEqualTo("foodsharing Stadt Zürich");

        properties.getMail().setFrom("foodsharing <noreply@foodsharing.test>");
        message.setFromName("foodsharing Bern");
        assertThat(delivery.from(message)).isEqualTo("foodsharing Bern <noreply@foodsharing.test>");
        message.setFromName(" ");
        assertThat(delivery.from(message)).isEqualTo("foodsharing <noreply@foodsharing.test>");
        // a mailbox mail keeps its own sender
        message.setFromAddress("zuerich@local.test");
        message.setFromName("Zürich Team");
        assertThat(new jakarta.mail.internet.InternetAddress(delivery.from(message)).getAddress())
                .isEqualTo("zuerich@local.test");
    }

    @Test
    void enqueueSetsTheRegionSenderAsFromName() {
        MailTemplateService templates = mock(MailTemplateService.class);
        when(templates.render(any(), any(), any())).thenReturn(new RenderedMail("S", "t", "<p>h</p>", Language.DE));
        when(repository.save(any())).thenAnswer(invocation -> {
            MailOutboxMessage saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        MailRateLimiter limiter = mock(MailRateLimiter.class);
        when(limiter.check(any(), any())).thenReturn(MailRateLimiter.Decision.allow());
        MailOutboxService outbox = new MailOutboxService(repository, templates, mock(MailDeliveryService.class),
                history, properties, clock, limiter);
        org.mockito.ArgumentCaptor<MailOutboxMessage> saved = org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);

        outbox.enqueue(new MailDraft("a@example.org", "magic-link", Language.DE,
                java.util.Map.of("senderRegion", "Stadt Zürich")));
        outbox.enqueue(new MailDraft("b@example.org", "magic-link", Language.DE, java.util.Map.of()));

        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getFromName()).isEqualTo("foodsharing Stadt Zürich");
        assertThat(saved.getAllValues().get(0).getFromAddress()).isNull();
        assertThat(saved.getAllValues().get(1).getFromName()).isNull();
    }

    @Test
    void retriesWithExponentialBackoffThenFails() {
        transport.failWith(new MailSendException("smtp down"));

        delivery.deliver(message.getId());
        assertThat(message.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isEqualTo(FixedClockConfig.SEED_NOW.plus(Duration.ofMinutes(1)));
        assertThat(message.getLastError()).contains("smtp down");

        delivery.deliver(message.getId());
        assertThat(message.getAttempts()).isEqualTo(2);
        assertThat(message.getNextAttemptAt()).isEqualTo(FixedClockConfig.SEED_NOW.plus(Duration.ofMinutes(2)));
        verify(history, never()).record(any());

        delivery.deliver(message.getId());
        assertThat(message.getAttempts()).isEqualTo(3);
        assertThat(message.getStatus()).isEqualTo(MailStatus.FAILED);
        verify(history).record(argThat(event -> event.action() == MailAction.FAILED));

        assertThat(delivery.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(delivery.backoff(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(delivery.backoff(8)).isEqualTo(Duration.ofMinutes(128));
    }

    @Test
    void withoutTransportTheMailStaysPending() {
        MailDeliveryService noTransport = new MailDeliveryService(repository,
                new StaticListableBeanFactory().getBeanProvider(MailTransport.class), history, properties, clock,
                mock(IntegrationRegistry.class), mock(network.foodsharing.upload.service.UploadService.class));
        noTransport.deliver(message.getId());
        assertThat(message.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(message.getAttempts()).isZero();
    }

    @Test
    void jobDeliversTheDueBatch() {
        properties.getMail().setOutboxBatchSize(5);
        UUID other = UUID.randomUUID();
        when(repository.findDueIds(eq(MailStatus.PENDING), eq(FixedClockConfig.SEED_NOW), any()))
                .thenReturn(List.of(message.getId(), other));
        MailDeliveryService deliveryMock = mock(MailDeliveryService.class);
        MailOutboxService outbox = new MailOutboxService(repository, mock(MailTemplateService.class), deliveryMock,
                history, properties, clock, mock(MailRateLimiter.class));

        new MailOutboxJob(mock(JobRunner.class), outbox, mock(IntegrationRegistry.class)).runNow();

        verify(deliveryMock).deliver(message.getId());
        verify(deliveryMock).deliver(other);
        verify(repository, times(1)).findDueIds(eq(MailStatus.PENDING), eq(FixedClockConfig.SEED_NOW),
                argThat(page -> page.getPageSize() == 5));
    }
}
