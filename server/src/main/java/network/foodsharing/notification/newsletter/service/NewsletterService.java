package network.foodsharing.notification.newsletter.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NewsletterStatus;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NewsletterRegions;
import network.foodsharing.notification.newsletter.domain.Newsletter;
import network.foodsharing.notification.newsletter.domain.NewsletterRecipient;
import network.foodsharing.notification.newsletter.repository.NewsletterRecipientRepository;
import network.foodsharing.notification.newsletter.repository.NewsletterRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Newsletters (N7): global ones are ORGA only, region ones belong to the region's admins (ambassadors, ORGA).
 * Recipients are active users with {@code newsletter_opt_in}, for a region additionally members of its subtree.
 * {@link #queue} freezes the recipients as PENDING rows that {@link NewsletterSendJob} turns into outbox mails.
 */
@Service
public class NewsletterService {

    static final String TEMPLATE = "notification/NEWSLETTER";
    static final int SUBJECT_MAX = 200;
    static final int BODY_MAX = 32600;

    private final NewsletterRepository newsletters;
    private final NewsletterRecipientRepository recipients;
    private final UserService users;
    private final NewsletterRegions regions;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;
    private final MailOutboxService outbox;
    private final NewsletterTokenService tokens;
    private final HistoryService history;
    private final Clock clock;

    public NewsletterService(NewsletterRepository newsletters, NewsletterRecipientRepository recipients,
                             UserService users, NewsletterRegions regions, PermissionService permissions,
                             CurrentActorService currentActor, MailOutboxService outbox, NewsletterTokenService tokens,
                             HistoryService history, Clock clock) {
        this.newsletters = newsletters;
        this.recipients = recipients;
        this.users = users;
        this.regions = regions;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.outbox = outbox;
        this.tokens = tokens;
        this.history = history;
        this.clock = clock;
    }

    /**
     * ORGA: all newsletters (or those of {@code regionId}); region admins: those of their regions.
     *
     * @throws ApiException 400 VALIDATION_FAILED, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public Page<Newsletter> list(UUID regionId, NewsletterStatus status, Integer page, Integer size) {
        int pageSize = size == null ? 20 : size;
        int pageNumber = page == null ? 0 : page;
        if (pageSize < 1 || pageSize > 100 || pageNumber < 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "page");
        }
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean all = false;
        Set<UUID> regionIds = new HashSet<>();
        if (regionId != null) {
            requireRegion(actor, regionId);
            regionIds.add(regionId);
        } else if (permissions.isOrga(actor)) {
            all = true;
        } else {
            regionIds.addAll(regions.adminRegionIds(actor.userId()));
            if (regionIds.isEmpty()) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
        }
        if (regionIds.isEmpty()) {
            regionIds.add(new UUID(0L, 0L));
        }
        return newsletters.search(all, regionIds, status == null, status, PageRequest.of(pageNumber, pageSize));
    }

    /** @throws ApiException 400 VALIDATION_FAILED, 403 (global: ORGA only), 404 REGION_NOT_FOUND */
    public Newsletter create(UUID regionId, String subject, String bodyMarkdown) {
        return create(network.foodsharing.notification.newsletter.domain.NewsletterKind.NEWSLETTER, regionId, subject, bodyMarkdown);
    }

    /** Info mails (A20) are global and ORGA only. @throws ApiException 400 VALIDATION_FAILED, 403, 404 */
    @Transactional
    public Newsletter create(network.foodsharing.notification.newsletter.domain.NewsletterKind kind, UUID regionId, String subject,
                             String bodyMarkdown) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (kind == network.foodsharing.notification.newsletter.domain.NewsletterKind.INFO_MAIL && regionId != null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "regionId");
        }
        if (regionId != null) {
            regions.find(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        }
        requireRegion(actor, regionId);
        Newsletter newsletter = new Newsletter();
        newsletter.setRegionId(regionId);
        newsletter.setKind(kind == null ? network.foodsharing.notification.newsletter.domain.NewsletterKind.NEWSLETTER : kind);
        newsletter.setSubject(text(subject, SUBJECT_MAX, "subject"));
        newsletter.setBodyMarkdown(text(bodyMarkdown, BODY_MAX, "bodyMarkdown"));
        newsletter = newsletters.saveAndFlush(newsletter);
        record(NotificationAction.NEWSLETTER_CREATED, newsletter);
        return newsletter;
    }

    /** @throws ApiException 403, 404 NEWSLETTER_NOT_FOUND */
    @Transactional(readOnly = true)
    public Newsletter get(UUID newsletterId) {
        return requireManaged(newsletterId);
    }

    /** @throws ApiException 400, 403, 404, 409 NEWSLETTER_NOT_DRAFT */
    @Transactional
    public Newsletter update(UUID newsletterId, String subject, String bodyMarkdown) {
        Newsletter newsletter = requireDraft(newsletterId);
        if (subject != null) {
            newsletter.setSubject(text(subject, SUBJECT_MAX, "subject"));
        }
        if (bodyMarkdown != null) {
            newsletter.setBodyMarkdown(text(bodyMarkdown, BODY_MAX, "bodyMarkdown"));
        }
        record(NotificationAction.NEWSLETTER_UPDATED, newsletter);
        return newsletter;
    }

    /** @throws ApiException 403, 404, 409 NEWSLETTER_NOT_DRAFT */
    @Transactional
    public void delete(UUID newsletterId) {
        Newsletter newsletter = requireDraft(newsletterId);
        newsletters.delete(newsletter);
        record(NotificationAction.NEWSLETTER_CANCELLED, newsletter);
    }

    /** @throws ApiException 403, 404 */
    @Transactional(readOnly = true)
    public int recipientCount(UUID newsletterId) {
        return recipientsOf(requireManaged(newsletterId)).size();
    }

    /** The newsletter as a mail to the current user (unsubscribe link included). @throws ApiException 403, 404 */
    @Transactional
    public void sendTest(UUID newsletterId) {
        Newsletter newsletter = requireManaged(newsletterId);
        User me = users.require(currentActor.requirePrincipal().userId());
        outbox.enqueue(draft(newsletter, me, "newsletter-test-" + newsletter.getId() + "-" + clock.millis()));
        history.record(history.event(NotificationAction.NEWSLETTER_QUEUED, newsletter.getId())
                .metadata(Map.of("test", true)).build());
    }

    /** DRAFT → QUEUED with frozen recipients. @throws ApiException 403, 404, 409 NOT_DRAFT / NO_RECIPIENTS */
    @Transactional
    public Newsletter queue(UUID newsletterId) {
        Newsletter newsletter = requireDraft(newsletterId);
        List<UUID> ids = recipientsOf(newsletter);
        if (ids.isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.NEWSLETTER_NO_RECIPIENTS);
        }
        for (UUID userId : ids) {
            NewsletterRecipient recipient = new NewsletterRecipient();
            recipient.setNewsletterId(newsletter.getId());
            recipient.setUserId(userId);
            recipients.save(recipient);
        }
        newsletter.setStatus(NewsletterStatus.QUEUED);
        newsletter.setQueuedAt(clock.instant());
        newsletter.setRecipientCount(ids.size());
        record(NotificationAction.NEWSLETTER_QUEUED, newsletter);
        return newsletter;
    }

    /** QUEUED/SENDING → CANCELLED; pending recipients are dropped. @throws ApiException 403, 404, 409 */
    @Transactional
    public Newsletter cancel(UUID newsletterId) {
        Newsletter newsletter = requireManaged(newsletterId);
        if (newsletter.getStatus() != NewsletterStatus.QUEUED && newsletter.getStatus() != NewsletterStatus.SENDING) {
            throw ApiException.conflict(ApiErrorCode.NEWSLETTER_NOT_CANCELLABLE);
        }
        recipients.deleteByNewsletterIdAndStatus(newsletter.getId(), NewsletterRecipient.Status.PENDING);
        newsletter.setStatus(NewsletterStatus.CANCELLED);
        record(NotificationAction.NEWSLETTER_CANCELLED, newsletter);
        return newsletter;
    }

    /** Recipients of the newsletter right now (active, opted in, members of the region subtree). */
    List<UUID> recipientsOf(Newsletter newsletter) {
        if (newsletter.getKind() == network.foodsharing.notification.newsletter.domain.NewsletterKind.INFO_MAIL) {
            return users.findActiveInfoMailRecipients().stream().map(User::getId).toList();
        }
        List<User> optedIn = users.findActiveNewsletterRecipients();
        if (newsletter.getRegionId() == null) {
            return optedIn.stream().map(User::getId).toList();
        }
        Set<UUID> members = new HashSet<>(regions.memberIdsOfSubtree(newsletter.getRegionId()));
        return optedIn.stream().map(User::getId).filter(members::contains).toList();
    }

    MailDraft draft(Newsletter newsletter, User user, String identifier) {
        return new MailDraft(user.getEmail(), TEMPLATE, user.getPreferredLanguage(), Map.of(
                "displayName", user.getDisplayName(), "subject", newsletter.getSubject(),
                "body", newsletter.getBodyMarkdown(),
                "bodyHtml", NewsletterMarkdown.toHtml(newsletter.getBodyMarkdown()), "unsubscribeUrl", tokens.unsubscribeUrl(user.getId())),
                user.getId(), MailDraft.NEWSLETTER, identifier);
    }

    Newsletter requireManaged(UUID newsletterId) {
        Newsletter newsletter = newsletters.findById(newsletterId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.NEWSLETTER_NOT_FOUND));
        requireRegion(currentActor.requirePrincipal(), newsletter.getRegionId());
        return newsletter;
    }

    private Newsletter requireDraft(UUID newsletterId) {
        Newsletter newsletter = requireManaged(newsletterId);
        if (newsletter.getStatus() != NewsletterStatus.DRAFT) {
            throw ApiException.conflict(ApiErrorCode.NEWSLETTER_NOT_DRAFT);
        }
        return newsletter;
    }

    private void requireRegion(SessionPrincipal actor, UUID regionId) {
        boolean allowed = regionId == null ? permissions.isOrga(actor)
                : permissions.mayAdministrateRegion(actor, regionId);
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", field);
        }
        return value.strip();
    }

    private void record(NotificationAction action, Newsletter newsletter) {
        var event = history.event(action, newsletter.getId());
        if (newsletter.getRegionId() != null) {
            event.region(newsletter.getRegionId());
        }
        history.record(event.metadata("status", newsletter.getStatus().name()).build());
    }
}
