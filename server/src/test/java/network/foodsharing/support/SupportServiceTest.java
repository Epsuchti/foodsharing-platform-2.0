package network.foodsharing.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.support.domain.SupportTicket;
import network.foodsharing.support.repository.SupportTicketRepository;
import network.foodsharing.support.service.SupportService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

/** C9: ticket stored and mailed with page/UA/attachment links, ORGA day bell, rate limit, toggle. */
class SupportServiceTest {

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final UploadService uploads = mock(UploadService.class);
    private final MailOutboxService mails = mock(MailOutboxService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FeatureToggleService toggles = mock(FeatureToggleService.class);
    private final ConfigurationService configuration = mock(ConfigurationService.class);
    private SupportService service;
    private User user;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getSupport().setMaxPerHour(2);
        service = new SupportService(tickets, users, actors, uploads, mails, notifications, toggles, configuration,
                mock(HistoryService.class, Answers.RETURNS_DEEP_STUBS), properties,
                Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC));
        when(tickets.saveAndFlush(any())).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(UUID.randomUUID());
            return ticket;
        });
        when(configuration.string(ConfigurationKey.SUPPORT_EMAIL)).thenReturn("support@foodsharing.network");
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("me@local");
        user.setDisplayName("Me");
        user.setFirstName("Mia");
        when(actors.requireUser()).thenReturn(user);
        User orga = new User();
        orga.setId(UUID.randomUUID());
        when(users.findActiveByRole(Role.ORGA)).thenReturn(List.of(orga));
        when(tickets.countByCreatedAtGreaterThanEqual(any())).thenReturn(3L);
    }

    private static SupportService.TicketInput input(List<UUID> attachments) {
        return new SupportService.TicketInput("Hilfe", "Text", "/stores/1", "Firefox", attachments, null, null);
    }

    @Test
    void ticketIsMailedWithPageAgentAndAttachmentLinks() {
        Upload upload = new Upload();
        upload.setId(UUID.randomUUID());
        upload.setOwnerUserId(user.getId());
        upload.setFileName("screen.png");
        when(uploads.find(upload.getId())).thenReturn(upload);
        when(uploads.claim(any(), any())).thenReturn(upload);

        SupportTicket ticket = service.create(input(List.of(upload.getId())), "198.51.100.1");

        assertThat(ticket.getEmail()).isEqualTo("me@local");
        assertThat(ticket.getFirstName()).isEqualTo("Mia");
        assertThat(ticket.getAttachmentUploadIds()).isEqualTo(upload.getId().toString());
        verify(uploads).claim(upload.getId(), new UploadUsage(SupportService.ATTACHMENT_MODULE, ticket.getId()));
        ArgumentCaptor<MailDraft> mail = ArgumentCaptor.forClass(MailDraft.class);
        verify(mails).enqueue(mail.capture());
        assertThat(mail.getValue().to()).isEqualTo("support@foodsharing.network");
        assertThat(mail.getValue().variables()).containsEntry("page", "/stores/1").containsEntry("userAgent", "Firefox");
        assertThat((String) mail.getValue().variables().get("attachments"))
                .contains("screen.png").contains("/api/uploads/" + upload.getId());
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().params()).containsEntry("count", "3");
        assertThat(bell.getValue().identifier()).isEqualTo("support-tickets-2026-10-28");
        verify(notifications).removeBells("support-tickets-2026-10-28");
    }

    @Test
    void foreignAttachmentIsRejected() {
        Upload upload = new Upload();
        upload.setId(UUID.randomUUID());
        upload.setOwnerUserId(UUID.randomUUID());
        when(uploads.find(upload.getId())).thenReturn(upload);
        assertThatThrownBy(() -> service.create(input(List.of(upload.getId())), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void rateLimitedPerUserAndPerIp() {
        service.create(input(null), "198.51.100.1");
        service.create(input(null), "198.51.100.2");
        assertThatThrownBy(() -> service.create(input(null), "198.51.100.3"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.SUPPORT_RATE_LIMITED);
        SupportService.TicketInput anonymous = new SupportService.TicketInput("Hilfe", "Text", null, null, null,
                "guest@example.ch", "Gast");
        service.createPublic(anonymous, "203.0.113.1");
        service.createPublic(anonymous, "203.0.113.1");
        assertThatThrownBy(() -> service.createPublic(anonymous, "203.0.113.1"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.SUPPORT_RATE_LIMITED);
    }

    @Test
    void publicFormValidatesTheEmailAndRespectsTheToggle() {
        assertThatThrownBy(() -> service.createPublic(new SupportService.TicketInput("a", "b", null, null, null,
                "kein-mail", "X"), null)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        doThrow(ApiException.notFound(ApiErrorCode.FEATURE_DISABLED)).when(toggles)
                .requireEnabled(FeatureToggle.SUPPORT_FORM);
        assertThatThrownBy(() -> service.create(input(null), null)).isInstanceOf(ApiException.class);
        verify(mails, never()).enqueue(any());
        verify(notifications, never()).removeBells(anyString());
    }
}
