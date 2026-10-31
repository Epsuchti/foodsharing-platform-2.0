package network.foodsharing.report.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.Report;
import network.foodsharing.report.domain.ReportAction;
import network.foodsharing.report.domain.ReportReason;
import network.foodsharing.report.domain.ReportStatus;
import network.foodsharing.report.domain.ReportWallPost;
import network.foodsharing.report.repository.ReportRepository;
import network.foodsharing.report.repository.ReportWallPostRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports (G1, upstream ReportRestController): filing, the handlers' list/detail/edit, the internal wall, ORGA
 * delete and the reminders. Every change records {@link ReportAction} history (the report log, D10.3).
 */
@Service
public class ReportService implements network.foodsharing.user.service.UserViolationCounter {

    static final String MAIL_CONFIRMATION = "report/confirmation";
    public static final int MAX_PAGE_SIZE = 100;

    /** Input of a new report. */
    public record ReportInput(ReportReason reason, String reasonText, String message, UUID storeId,
                              boolean sendConfirmationMail) {
    }

    /** Requested changes; null = unchanged, a blank consequence and the clear flags remove a value. */
    public record ReportChange(ReportStatus status, String consequence, Instant reminderAt, boolean clearReminder,
                               UUID forumThreadId, boolean clearForumThread) {
    }

    /** A wall post with its delete right. */
    public record PostView(ReportWallPost post, boolean mayDelete) {
    }

    /** A report with what the mapper needs. */
    public record ReportView(Report report, Store store, boolean mayEdit, boolean mayDelete, boolean mayPost) {
    }

    private final ReportRepository reports;
    private final ReportWallPostRepository posts;
    private final ReportPermissionService access;
    private final RegionService regions;
    private final StoreQueryService stores;
    private final UserService users;
    private final CurrentActorService actors;
    private final NotificationService notifications;
    private final MailOutboxService mails;
    private final HistoryService history;
    private final Clock clock;

    public ReportService(ReportRepository reports, ReportWallPostRepository posts, ReportPermissionService access,
                         RegionService regions, StoreQueryService stores, UserService users,
                         CurrentActorService actors, NotificationService notifications, MailOutboxService mails,
                         HistoryService history, Clock clock) {
        this.reports = reports;
        this.posts = posts;
        this.access = access;
        this.regions = regions;
        this.stores = stores;
        this.users = users;
        this.actors = actors;
        this.notifications = notifications;
        this.mails = mails;
        this.history = history;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------ filing

    /**
     * Files a report about {@code userId}; the report team of the report's region gets a {@code REPORT_NEW} bell.
     *
     * @throws ApiException 404 USER_NOT_FOUND / STORE_NOT_FOUND, 409 REPORT_SELF, 403 ACCESS_DENIED (unverified),
     *                      403 REPORT_BUTTON_DISABLED, 400 VALIDATION_FAILED (OTHER without text),
     *                      400 REPORT_REASON_NOT_ALLOWED (reason outside the region's set, see {@link #options})
     */
    @Transactional
    public Report create(UUID userId, ReportInput input) {
        SessionPrincipal actor = actors.requirePrincipal();
        User reporter = users.requireActive(actor.userId());
        User reported = users.findActive(userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        ApiErrorCode denial = ReportRules.reportDenial(access.reporterFacts(reporter, reported));
        if (denial == ApiErrorCode.REPORT_SELF) {
            throw ApiException.conflict(denial);
        }
        if (denial != null) {
            throw ApiException.forbidden(denial);
        }
        if (input.reason() == null || !ReportRules.reasonTextValid(input.reason(), input.reasonText())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "reasonText");
        }
        if (input.message() == null || input.message().strip().length() < 10) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "message");
        }
        UUID regionId = reportRegion(reported, input.storeId());
        if (regionId == null) {
            throw ApiException.forbidden(ApiErrorCode.REPORT_BUTTON_DISABLED);
        }
        var regionOptions = access.regionOptions(regionId);
        if (!ReportRules.reasonAllowed(input.reason(), regionOptions.reportReasonOptions(),
                regionOptions.reportReasonOther())) {
            throw ApiException.badRequest(ApiErrorCode.REPORT_REASON_NOT_ALLOWED).withArg("field", "reason");
        }
        Report report = new Report();
        report.setRegionId(regionId);
        report.setReportedUserId(reported.getId());
        report.setReporterId(reporter.getId());
        report.setReason(input.reason());
        report.setReasonText(input.reason() == ReportReason.OTHER ? input.reasonText().strip() : null);
        report.setMessage(input.message().strip());
        report.setStoreId(input.storeId());
        report.setStatus(ReportStatus.TO_DO);
        Report saved = reports.save(report);
        history.record(history.event(ReportAction.CREATED, saved.getId()).secondarySubject(reported.getId())
                .region(regionId).metadata("reason", input.reason().name()).build());
        Set<UUID> recipients = recipients(regionId);
        recipients.remove(reporter.getId());
        recipients.remove(reported.getId());
        if (!recipients.isEmpty()) {
            notifications.notify(NotificationRequest.to(recipients, NotificationType.REPORT_NEW)
                    .param("name", reported.getDisplayName()).link(link(saved.getId()))
                    .identifier(newBell(saved.getId())).build());
        }
        if (input.sendConfirmationMail() && reporter.getEmail() != null) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("displayName", reporter.getDisplayName());
            variables.put("reportedName", reported.getDisplayName());
            variables.put("reason", input.reason().name());
            variables.put("message", saved.getMessage());
            mails.enqueue(new MailDraft(reporter.getEmail(), MAIL_CONFIRMATION, reporter.getPreferredLanguage(),
                    variables, reporter.getId(), NotificationType.REPORT_NEW.category().name(),
                    "REPORT_CONFIRMATION-" + saved.getId()));
        }
        return saved;
    }

    /** What the report dialog offers; {@code denial} = why {@link #create} would refuse (null = it would not). */
    public record ReportOptionsView(UUID regionId, List<ReportReason> reasons, boolean otherAllowed,
                                    ApiErrorCode denial) {
    }

    /**
     * Reason options of the region a report about {@code userId} would go to (same resolution as {@link #create}; the
     * actor need not be a member there) and whether the actor may file it.
     *
     * @throws ApiException 404 USER_NOT_FOUND / STORE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public ReportOptionsView options(UUID userId, UUID storeId) {
        SessionPrincipal actor = actors.requirePrincipal();
        User reporter = users.requireActive(actor.userId());
        User reported = users.findActive(userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        UUID regionId = reportRegion(reported, storeId);
        ApiErrorCode denial = ReportRules.reportDenial(access.reporterFacts(reporter, reported));
        if (regionId == null) {
            return new ReportOptionsView(null, ReportRules.reasonOptions(1), false,
                    denial != null ? denial : ApiErrorCode.REPORT_BUTTON_DISABLED);
        }
        var regionOptions = access.regionOptions(regionId);
        return new ReportOptionsView(regionId, ReportRules.reasonOptions(regionOptions.reportReasonOptions()),
                regionOptions.reportReasonOther(), denial);
    }

    /** The report's region (shared by {@link #create} and {@link #options}). @throws ApiException 404 STORE_NOT_FOUND */
    private UUID reportRegion(User reported, UUID storeId) {
        UUID storeRegion = null;
        if (storeId != null) {
            storeRegion = stores.findLive(storeId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.STORE_NOT_FOUND)).getRegionId();
        }
        return ReportRules.reportRegion(storeRegion, reported.getHomeRegionId());
    }

    // ------------------------------------------------------------------------------------------ handling

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE */
    @Transactional(readOnly = true)
    public Page<Report> list(UUID regionId, ReportStatus status, Integer page, Integer size) {
        regions.require(regionId);
        access.requireHandler(actors.requirePrincipal(), regionId);
        return reports.list(regionId, status, PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
    }

    /**
     * Reports about a member that the actor may handle (profile).
     *
     * @throws ApiException 404 USER_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE
     */
    @Transactional(readOnly = true)
    public List<Report> listForUser(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        users.require(userId);
        if (!access.mayAccessReportsForUser(actor, userId)) {
            throw ApiException.forbidden(ApiErrorCode.REPORTS_NOT_ACCESSIBLE);
        }
        Map<UUID, Boolean> regionAccess = new HashMap<>();
        return reports.findByReportedUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(r -> regionAccess.computeIfAbsent(r.getRegionId(), id -> access.mayHandle(actor, id)))
                .toList();
    }

    /**
     * Profile violation count: the reports about {@code userId} that {@link #listForUser} would show the actor, or
     * empty when the actor may not see the user's reports at all.
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.OptionalInt visibleReportCount(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (!access.mayAccessReportsForUser(actor, userId)) {
            return java.util.OptionalInt.empty();
        }
        Map<UUID, Boolean> regionAccess = new HashMap<>();
        return java.util.OptionalInt.of((int) reports.findByReportedUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId)
                .stream().filter(r -> regionAccess.computeIfAbsent(r.getRegionId(), id -> access.mayHandle(actor, id)))
                .count());
    }

    /** @throws ApiException 404 REPORT_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE */
    @Transactional(readOnly = true)
    public ReportView get(UUID reportId) {
        Report report = requireHandled(reportId);
        return view(report);
    }

    /**
     * Status, consequence, reminder and linked thread; each change is one history entry.
     *
     * @throws ApiException 404 REPORT_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE, 400 VALIDATION_FAILED (DELETED status)
     */
    @Transactional
    public ReportView update(UUID reportId, ReportChange change) {
        Report report = requireHandled(reportId);
        if (change.status() == ReportStatus.DELETED) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "status");
        }
        if (change.clearReminder() && change.reminderAt() != null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "clearReminder");
        }
        if (change.clearForumThread() && change.forumThreadId() != null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "clearForumThread");
        }
        if (change.status() != null && change.status() != report.getStatus()) {
            record(ReportAction.STATUS_CHANGED, report, "status", report.getStatus(), change.status());
            report.setStatus(change.status());
        }
        if (change.consequence() != null) {
            String consequence = change.consequence().isBlank() ? null : change.consequence().strip();
            if (!Objects.equals(consequence, report.getConsequence())) {
                record(ReportAction.CONSEQUENCE_SET, report, "consequence", report.getConsequence(), consequence);
                report.setConsequence(consequence);
            }
        }
        Instant reminderAt = change.clearReminder() ? null : change.reminderAt();
        if ((change.clearReminder() || reminderAt != null) && !Objects.equals(reminderAt, report.getReminderAt())) {
            record(ReportAction.REMINDER_SET, report, "reminderAt", report.getReminderAt(), reminderAt);
            report.setReminderAt(reminderAt);
        }
        UUID threadId = change.clearForumThread() ? null : change.forumThreadId();
        if ((change.clearForumThread() || threadId != null) && !Objects.equals(threadId, report.getForumThreadId())) {
            record(ReportAction.THREAD_LINKED, report, "forumThreadId", report.getForumThreadId(), threadId);
            report.setForumThreadId(threadId);
        }
        return view(reports.save(report));
    }

    /** Soft delete (ORGA). @throws ApiException 404 REPORT_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID reportId) {
        SessionPrincipal actor = actors.requirePrincipal();
        Report report = require(reportId);
        if (!ReportRules.mayDelete(access.isOrga(actor))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        record(ReportAction.DELETED, report, "status", report.getStatus(), ReportStatus.DELETED);
        report.setStatus(ReportStatus.DELETED);
        report.setDeletedAt(clock.instant());
        reports.save(report);
        notifications.removeBells(newBell(reportId));
        notifications.removeBells(reminderBell(reportId));
    }

    // ------------------------------------------------------------------------------------------ wall

    /** @throws ApiException 404 REPORT_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE */
    @Transactional(readOnly = true)
    public Page<PostView> wall(UUID reportId, Integer page, Integer size) {
        requireHandled(reportId);
        SessionPrincipal actor = actors.requirePrincipal();
        boolean orga = access.isOrga(actor);
        return posts.findByReportIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(reportId,
                        PageRequest.of(page == null ? 0 : Math.max(page, 0),
                                size == null ? 20 : Math.min(Math.max(size, 1), MAX_PAGE_SIZE)))
                .map(p -> new PostView(p, ReportRules.mayDeleteWallPost(orga, p.getAuthorId().equals(actor.userId()))));
    }

    /** Newest wall posts of a report (detail view). */
    @Transactional(readOnly = true)
    public List<PostView> latestWall(UUID reportId, int limit) {
        return wall(reportId, 0, limit).getContent();
    }

    /** @throws ApiException 404 REPORT_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE, 400 VALIDATION_FAILED */
    @Transactional
    public ReportWallPost post(UUID reportId, String body) {
        Report report = requireHandled(reportId);
        if (body == null || body.isBlank() || body.strip().length() > 4000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "body");
        }
        ReportWallPost post = new ReportWallPost();
        post.setReportId(reportId);
        post.setAuthorId(actors.requirePrincipal().userId());
        post.setBody(body.strip());
        ReportWallPost saved = posts.save(post);
        history.record(history.event(ReportAction.WALL_POST_CREATED, reportId).region(report.getRegionId())
                .metadata("postId", saved.getId().toString()).build());
        return saved;
    }

    /** @throws ApiException 404 REPORT_NOT_FOUND / WALL_POST_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional
    public void deletePost(UUID reportId, UUID postId) {
        SessionPrincipal actor = actors.requirePrincipal();
        Report report = require(reportId);
        ReportWallPost post = posts.findByIdAndReportIdAndDeletedAtIsNull(postId, reportId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.WALL_POST_NOT_FOUND));
        boolean orga = access.isOrga(actor);
        if (!orga) {
            access.requireHandler(actor, report.getRegionId());
        }
        if (!ReportRules.mayDeleteWallPost(orga, post.getAuthorId().equals(actor.userId()))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        post.setDeletedAt(clock.instant());
        posts.save(post);
        history.record(history.event(ReportAction.WALL_POST_DELETED, reportId).region(report.getRegionId())
                .metadata("postId", postId.toString()).build());
    }

    // ------------------------------------------------------------------------------------------ reminders

    /**
     * Sends a {@code REPORT_REMINDER} bell for every open report whose reminder is due and clears the reminder
     * ({@code report-reminders} job).
     */
    @Transactional
    public int sendDueReminders() {
        Instant now = clock.instant();
        int sent = 0;
        for (Report report : reports.dueReminders(now, ReportStatus.COMPLETED)) {
            Set<UUID> recipients = recipients(report.getRegionId());
            if (!recipients.isEmpty()) {
                String name = users.findActive(report.getReportedUserId()).map(User::getDisplayName).orElse("");
                notifications.removeBells(reminderBell(report.getId()));
                notifications.notify(NotificationRequest.to(recipients, NotificationType.REPORT_REMINDER)
                        .param("name", name).link(link(report.getId())).identifier(reminderBell(report.getId()))
                        .build());
                sent++;
            }
            history.record(history.event(ReportAction.REMINDER_SET, report.getId()).region(report.getRegionId())
                    .change("reminderAt", report.getReminderAt(), null).metadata("delivered", true).build());
            report.setReminderAt(null);
            reports.save(report);
        }
        return sent;
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** Handlers of the region, else of the nearest ancestor that has any (nobody is left without a team). */
    Set<UUID> recipients(UUID regionId) {
        Set<UUID> direct = access.handlers(regionId);
        if (!direct.isEmpty()) {
            return new LinkedHashSet<>(direct);
        }
        List<Region> chain = regions.ancestors(regionId);
        for (int i = chain.size() - 1; i >= 0; i--) {
            Set<UUID> handlers = access.handlers(chain.get(i).getId());
            if (!handlers.isEmpty()) {
                return new LinkedHashSet<>(handlers);
            }
        }
        return new LinkedHashSet<>();
    }

    private ReportView view(Report report) {
        SessionPrincipal actor = actors.requirePrincipal();
        Store store = report.getStoreId() == null ? null : stores.findLive(report.getStoreId()).orElse(null);
        return new ReportView(report, store, true, ReportRules.mayDelete(access.isOrga(actor)), true);
    }

    private Report require(UUID reportId) {
        return reports.findByIdAndDeletedAtIsNull(reportId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.REPORT_NOT_FOUND));
    }

    private Report requireHandled(UUID reportId) {
        Report report = require(reportId);
        access.requireHandler(actors.requirePrincipal(), report.getRegionId());
        return report;
    }

    private void record(ReportAction action, Report report, String field, Object oldValue, Object newValue) {
        history.record(history.event(action, report.getId()).secondarySubject(report.getReportedUserId())
                .region(report.getRegionId()).change(field, oldValue, newValue).build());
    }

    public Optional<Store> store(UUID storeId) {
        return storeId == null ? Optional.empty() : stores.findLive(storeId);
    }

    static String link(UUID reportId) {
        return "/reports/" + reportId;
    }

    static String newBell(UUID reportId) {
        return "REPORT_NEW-" + reportId;
    }

    static String reminderBell(UUID reportId) {
        return "REPORT_REMINDER-" + reportId;
    }
}
