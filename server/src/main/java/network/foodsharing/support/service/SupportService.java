package network.foodsharing.support.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.support.domain.SupportAction;
import network.foodsharing.support.domain.SupportTicket;
import network.foodsharing.support.repository.SupportTicketRepository;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support form (C9, upstream {@code SupportController}): the ticket is stored, mailed to {@code support.email} with
 * page, user agent and attachment links, and ORGA gets one {@code SUPPORT_TICKET} bell per day (count updated).
 * At most {@code app.support.rate-limit-per-hour} tickets per user and per IP (429 SUPPORT_RATE_LIMITED).
 * Needs the SUPPORT_FORM feature toggle.
 */
@Service
public class SupportService {

    public static final String ATTACHMENT_MODULE = "SUPPORT_ATTACHMENT";
    static final String MAIL_TICKET = "support/ticket";
    static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    public record TicketInput(String subject, String body, String page, String userAgent, List<UUID> attachmentUploadIds,
                              String email, String firstName) {
    }

    private final SupportTicketRepository tickets;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final UploadService uploads;
    private final MailOutboxService mails;
    private final NotificationService notifications;
    private final FeatureToggleService toggles;
    private final ConfigurationService configuration;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final SlidingWindowRateLimiter limiter;

    public SupportService(SupportTicketRepository tickets, UserService users, CurrentActorService currentActor,
                          UploadService uploads, MailOutboxService mails, NotificationService notifications,
                          FeatureToggleService toggles, ConfigurationService configuration, HistoryService history,
                          AppProperties properties, Clock clock) {
        this.tickets = tickets;
        this.users = users;
        this.currentActor = currentActor;
        this.uploads = uploads;
        this.mails = mails;
        this.notifications = notifications;
        this.toggles = toggles;
        this.configuration = configuration;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
        this.limiter = new SlidingWindowRateLimiter(clock, Math.max(1, properties.getSupport().getRateLimitPerHour()),
                Duration.ofHours(1));
    }

    /**
     * Ticket of the logged-in user (email and first name from the account).
     *
     * @throws ApiException 404 FEATURE_DISABLED, 429 SUPPORT_RATE_LIMITED, 403 ACCESS_DENIED (foreign attachment)
     */
    @Transactional
    public SupportTicket create(TicketInput input, String clientIp) {
        toggles.requireEnabled(FeatureToggle.SUPPORT_FORM);
        User user = currentActor.requireUser();
        checkRate("user:" + user.getId(), clientIp);
        return store(user.getId(), user.getEmail(), user.getFirstName() == null ? user.getDisplayName()
                : user.getFirstName(), input);
    }

    /** Ticket from the public form (no attachments). @throws ApiException 404 FEATURE_DISABLED, 429, 400 */
    @Transactional
    public SupportTicket createPublic(TicketInput input, String clientIp) {
        toggles.requireEnabled(FeatureToggle.SUPPORT_FORM);
        String email = input.email() == null ? "" : input.email().strip();
        if (!email.matches("[^@\\s]+@[^@\\s]+")) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "email");
        }
        checkRate(null, clientIp);
        return store(null, email, input.firstName() == null ? null : input.firstName().strip(),
                new TicketInput(input.subject(), input.body(), input.page(), input.userAgent(), null, email,
                        input.firstName()));
    }

    /** Does the ticket still exist (upload cleanup)? */
    @Transactional(readOnly = true)
    public boolean exists(UUID ticketId) {
        return tickets.existsById(ticketId);
    }

    /** Account deletion: the user's tickets are anonymised (kept for the support team without personal data). */
    @Transactional
    @NoHistory(reason = "part of the account deletion, which records the user deletion")
    public void anonymise(UUID userId) {
        for (SupportTicket ticket : tickets.findByUserId(userId)) {
            ticket.setUserId(null);
            ticket.setEmail("deleted@deleted.invalid");
            ticket.setFirstName(null);
            ticket.setUserAgent(null);
        }
    }

    private void checkRate(String userKey, String clientIp) {
        boolean allowed = (userKey == null || limiter.tryAcquire(userKey))
                && (clientIp == null || limiter.tryAcquire("ip:" + clientIp));
        if (!allowed) {
            throw ApiException.tooManyRequests(ApiErrorCode.SUPPORT_RATE_LIMITED, limiter.window());
        }
    }

    private SupportTicket store(UUID userId, String email, String firstName, TicketInput input) {
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(userId);
        ticket.setEmail(email);
        ticket.setFirstName(firstName);
        ticket.setSubject(input.subject().strip());
        ticket.setBody(input.body());
        ticket.setPage(StringUtils.abbreviate(StringUtils.trimToNull(input.page()), 500));
        ticket.setUserAgent(StringUtils.abbreviate(StringUtils.trimToNull(input.userAgent()), 500));
        SupportTicket saved = tickets.saveAndFlush(ticket);
        List<Upload> attachments = claimAttachments(saved, input.attachmentUploadIds(), userId);
        history.record(history.event(SupportAction.TICKET_CREATED, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(saved.getId())
                .metadata(Map.of("ticketId", saved.getId().toString(), "attachments", attachments.size(),
                        "loggedIn", userId != null)).build());
        mail(saved, attachments);
        bellOrga();
        return saved;
    }

    private List<Upload> claimAttachments(SupportTicket ticket, List<UUID> uploadIds, UUID userId) {
        List<Upload> result = new ArrayList<>();
        if (uploadIds == null || uploadIds.isEmpty() || userId == null) {
            return result;
        }
        for (UUID uploadId : new LinkedHashSet<>(uploadIds)) {
            Upload upload = uploads.find(uploadId);
            if (!userId.equals(upload.getOwnerUserId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
            result.add(uploads.claim(uploadId, new UploadUsage(ATTACHMENT_MODULE, ticket.getId())));
        }
        ticket.setAttachmentUploadIds(result.stream().map(u -> u.getId().toString()).collect(Collectors.joining(",")));
        return result;
    }

    private void mail(SupportTicket ticket, List<Upload> attachments) {
        String to = configuration.string(ConfigurationKey.SUPPORT_EMAIL);
        if (to == null || to.isBlank()) {
            return;
        }
        String base = StringUtils.removeEnd(properties.getBaseUrl(), "/");
        Map<String, Object> variables = new HashMap<>();
        variables.put("ticketId", ticket.getId().toString());
        variables.put("subject", ticket.getSubject());
        variables.put("body", ticket.getBody());
        variables.put("email", ticket.getEmail());
        variables.put("firstName", ticket.getFirstName() == null ? "-" : ticket.getFirstName());
        variables.put("loggedIn", ticket.getUserId() != null);
        variables.put("userId", ticket.getUserId() == null ? "-" : ticket.getUserId().toString());
        variables.put("page", ticket.getPage() == null ? "-" : ticket.getPage());
        variables.put("userAgent", ticket.getUserAgent() == null ? "-" : ticket.getUserAgent());
        variables.put("attachments", attachments.isEmpty() ? "-" : attachments.stream()
                .map(upload -> upload.getFileName() + ": " + base + "/api/uploads/" + upload.getId())
                .collect(Collectors.joining("\n")));
        mails.enqueue(new MailDraft(to.strip(), MAIL_TICKET, Language.DE, variables, null, null,
                "support-ticket-" + ticket.getId()));
    }

    private void bellOrga() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        long count = tickets.countByCreatedAtGreaterThanEqual(today.atStartOfDay(ZONE).toInstant());
        Set<UUID> recipients = users.findActiveByRole(Role.ORGA).stream().map(User::getId)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        String identifier = dayBell(today);
        notifications.removeBells(identifier);
        if (!recipients.isEmpty()) {
            notifications.notify(NotificationRequest.to(recipients, NotificationType.SUPPORT_TICKET)
                    .param("count", String.valueOf(Math.max(1, count))).param("date", today.toString())
                    .link("/admin").identifier(identifier).build());
        }
    }

    static String dayBell(LocalDate day) {
        return "support-tickets-" + day;
    }
}
