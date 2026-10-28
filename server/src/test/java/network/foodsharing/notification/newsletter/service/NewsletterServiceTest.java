package network.foodsharing.notification.newsletter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NewsletterStatus;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.newsletter.domain.Newsletter;
import network.foodsharing.notification.newsletter.domain.NewsletterRecipient;
import network.foodsharing.notification.newsletter.repository.NewsletterRecipientRepository;
import network.foodsharing.notification.newsletter.repository.NewsletterRepository;
import network.foodsharing.notification.service.NewsletterRegions;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

/** Newsletters (N7, M4 B5): scopes, recipients, queue/cancel states, send batches, unsubscribe tokens. */
class NewsletterServiceTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final UUID REGION = UUID.randomUUID();
    static final UUID ME = UUID.randomUUID();

    NewsletterRepository newsletters = mock(NewsletterRepository.class);
    NewsletterRecipientRepository recipients = mock(NewsletterRecipientRepository.class);
    UserService users = mock(UserService.class);
    NewsletterRegions regions = mock(NewsletterRegions.class);
    PermissionService permissions = mock(PermissionService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    MailOutboxService outbox = mock(MailOutboxService.class);
    AppProperties properties = new AppProperties();
    NewsletterTokenService tokens = new NewsletterTokenService(properties);
    NewsletterService service;
    Newsletter draft = new Newsletter();
    List<User> optedIn = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new NewsletterService(newsletters, recipients, users, regions, permissions, actor, outbox, tokens,
                mock(HistoryService.class, RETURNS_DEEP_STUBS), Clock.fixed(NOW, ZoneOffset.UTC));
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(ME, UUID.randomUUID(), Role.AMBASSADOR,
                "me@test.local"));
        when(permissions.mayAdministrateRegion(any(), eq(REGION))).thenReturn(true);
        when(regions.find(REGION)).thenReturn(Optional.of(new NewsletterRegions.RegionSummary(REGION, "zh", "Zürich",
                "CITY", null)));
        draft.setId(UUID.randomUUID());
        draft.setRegionId(REGION);
        draft.setSubject("Hallo");
        draft.setBodyMarkdown("Text");
        when(newsletters.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(newsletters.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(users.findActiveNewsletterRecipients()).thenAnswer(i -> optedIn);
    }

    static User user(boolean optIn) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(user.getId() + "@test.local");
        user.setDisplayName("U");
        user.setNewsletterOptIn(optIn);
        return user;
    }

    static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    @Test
    void globalNewslettersAreOrgaOnly() {
        assertThat(code(() -> service.create(null, "S", "B"))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(permissions.isOrga(any())).thenReturn(true);
        assertThat(service.create(null, "S", "B").getRegionId()).isNull();
        assertThat(service.create(REGION, "S", "B").getStatus()).isEqualTo(NewsletterStatus.DRAFT);
        assertThat(code(() -> service.create(UUID.randomUUID(), "S", "B"))).isEqualTo(ApiErrorCode.REGION_NOT_FOUND);
        assertThat(code(() -> service.create(REGION, " ", "B"))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void listScopes() {
        when(newsletters.search(anyBoolean(), anyCollection(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(regions.adminRegionIds(ME)).thenReturn(Set.of());
        assertThat(code(() -> service.list(null, null, 0, 20))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(regions.adminRegionIds(ME)).thenReturn(Set.of(REGION));
        service.list(null, null, 0, 20);
        verify(newsletters).search(eq(false), eq(Set.of(REGION)), eq(true), eq(null), any(Pageable.class));
        when(permissions.isOrga(any())).thenReturn(true);
        service.list(null, NewsletterStatus.SENT, 0, 20);
        verify(newsletters).search(eq(true), anyCollection(), eq(false), eq(NewsletterStatus.SENT),
                any(Pageable.class));
    }

    @Test
    void regionRecipientsAreOptedInMembersOfTheSubtree() {
        User member = user(true);
        User outsider = user(true);
        optedIn.addAll(List.of(member, outsider));
        when(regions.memberIdsOfSubtree(REGION)).thenReturn(List.of(member.getId(), UUID.randomUUID()));
        assertThat(service.recipientCount(draft.getId())).isEqualTo(1);
        draft.setRegionId(null);
        when(permissions.isOrga(any())).thenReturn(true);
        assertThat(service.recipientCount(draft.getId())).isEqualTo(2);
    }

    @Test
    void queueAndCancelStates() {
        assertThat(code(() -> service.queue(draft.getId()))).isEqualTo(ApiErrorCode.NEWSLETTER_NO_RECIPIENTS);
        User member = user(true);
        optedIn.add(member);
        when(regions.memberIdsOfSubtree(REGION)).thenReturn(List.of(member.getId()));
        assertThat(code(() -> service.cancel(draft.getId()))).isEqualTo(ApiErrorCode.NEWSLETTER_NOT_CANCELLABLE);
        service.queue(draft.getId());
        assertThat(draft.getStatus()).isEqualTo(NewsletterStatus.QUEUED);
        assertThat(draft.getRecipientCount()).isEqualTo(1);
        verify(recipients).save(any(NewsletterRecipient.class));
        assertThat(code(() -> service.queue(draft.getId()))).isEqualTo(ApiErrorCode.NEWSLETTER_NOT_DRAFT);
        assertThat(code(() -> service.update(draft.getId(), "x", null))).isEqualTo(ApiErrorCode.NEWSLETTER_NOT_DRAFT);
        assertThat(code(() -> service.delete(draft.getId()))).isEqualTo(ApiErrorCode.NEWSLETTER_NOT_DRAFT);
        service.cancel(draft.getId());
        assertThat(draft.getStatus()).isEqualTo(NewsletterStatus.CANCELLED);
        verify(recipients).deleteByNewsletterIdAndStatus(draft.getId(), NewsletterRecipient.Status.PENDING);
    }

    @Test
    void foreignRegionIsForbidden() {
        when(permissions.mayAdministrateRegion(any(), eq(REGION))).thenReturn(false);
        assertThat(code(() -> service.get(draft.getId()))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void sendJobQueuesABatchAndFinishes() {
        properties.getNewsletter().setBatchSize(2);
        properties.getNewsletter().setMailsPerMinute(100);
        draft.setStatus(NewsletterStatus.QUEUED);
        when(newsletters.findByStatusIn(any())).thenReturn(List.of(draft));
        User a = user(true);
        User b = user(true);
        User left = user(false);
        List<NewsletterRecipient> pending = new ArrayList<>();
        for (User u : List.of(left, a, b)) {
            NewsletterRecipient r = new NewsletterRecipient();
            r.setUserId(u.getId());
            pending.add(r);
            when(users.findActive(u.getId())).thenReturn(Optional.of(u));
        }
        when(recipients.findByNewsletterIdAndStatusOrderByCreatedAtAscIdAsc(eq(draft.getId()),
                eq(NewsletterRecipient.Status.PENDING), any())).thenReturn(pending);
        when(recipients.countByNewsletterIdAndStatus(draft.getId(), NewsletterRecipient.Status.PENDING))
                .thenReturn(0L);
        when(outbox.enqueue(any())).thenReturn(UUID.randomUUID());
        NewsletterSendJob job = new NewsletterSendJob(mock(JobRunner.class), newsletters, recipients, service, outbox,
                users, mock(HistoryService.class, RETURNS_DEEP_STUBS), properties, Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
        assertThat(job.sendBatch()).isEqualTo(2);
        ArgumentCaptor<MailDraft> mails = ArgumentCaptor.forClass(MailDraft.class);
        verify(outbox, times(2)).enqueue(mails.capture());
        MailDraft mail = mails.getAllValues().getFirst();
        assertThat(mail.category()).isEqualTo(MailDraft.NEWSLETTER);
        assertThat(mail.template()).isEqualTo("notification/NEWSLETTER");
        String url = (String) mail.variables().get("unsubscribeUrl");
        assertThat(tokens.verify(url.substring(url.lastIndexOf('/') + 1))).contains(a.getId());
        assertThat(pending.getFirst().getStatus()).isEqualTo(NewsletterRecipient.Status.FAILED);
        assertThat(draft.getSentCount()).isEqualTo(2);
        assertThat(draft.getFailedCount()).isEqualTo(1);
        assertThat(draft.getStatus()).isEqualTo(NewsletterStatus.SENT);
    }

    @Test
    void unsubscribeTokens() {
        UUID id = UUID.randomUUID();
        String token = tokens.token(id);
        assertThat(tokens.verify(token)).contains(id);
        assertThat(tokens.verify(token.substring(0, token.length() - 2) + "AA")).isEmpty();
        assertThat(tokens.verify("garbage")).isEmpty();
        properties.getNewsletter().setSecret("other");
        assertThat(tokens.verify(token)).isEmpty();
        assertThat(NewsletterUnsubscribeService.mask("sarah@example.org")).isEqualTo("s***h@example.org");
        assertThat(new PageImpl<>(List.of())).isEmpty();
    }
}
