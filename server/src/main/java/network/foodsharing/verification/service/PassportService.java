package network.foodsharing.verification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.verification.domain.PassportAction;
import network.foodsharing.verification.domain.PassportIssue;
import network.foodsharing.verification.domain.PassportKind;
import network.foodsharing.verification.domain.PassportRequest;
import network.foodsharing.verification.repository.PassportIssueRepository;
import network.foodsharing.verification.repository.PassportRequestRepository;
import network.foodsharing.verification.service.PassportPdfRenderer.Card;
import network.foodsharing.verification.service.PassportPdfRenderer.Paper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passports (plan §13.9 Ve3): validity of {@code app.verification.passport-validity-years} (3) from the start of the
 * last issue; own download (verified and valid), requests to the ambassadors, and batch generation for a region by an
 * ambassador or ORGA (renew = valid from today, explicit validFrom, or reprint with the current validity) producing
 * one PDF as a job result.
 */
@Service
public class PassportService {

    public enum Availability { AVAILABLE, NOT_CONFIGURED, NOT_VERIFIED }

    public record Validity(LocalDate validFrom, LocalDate validUntil) {
    }

    public record StatusView(boolean verified, Instant lastIssuedAt, LocalDate validFrom, LocalDate validUntil,
                             boolean valid, boolean requestPending, Availability apple, Availability google) {
    }

    public record RequestView(PassportRequest request, User user, User fulfilledBy) {
    }

    public record BatchInput(List<UUID> userIds, boolean createPdf, boolean renew, LocalDate validFrom, Paper paper,
                             boolean notifyUsers) {
    }

    /** After commit: PASSPORT_CREATED to the users (when asked to notify). */
    public record PassportsCreated(List<UUID> userIds, UUID regionId) {
    }

    /** After commit: GENERIC bell to the ambassadors of the home region. */
    public record PassportRequested(UUID userId, UUID regionId) {
    }

    private final PassportIssueRepository issues;
    private final PassportRequestRepository requests;
    private final PassportPdfRenderer renderer;
    private final WalletConfiguration wallets;
    private final UserService users;
    private final RegionService regions;
    private final MembershipService memberships;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ProgressTracker tracker;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    public PassportService(PassportIssueRepository issues, PassportRequestRepository requests,
                           PassportPdfRenderer renderer, WalletConfiguration wallets, UserService users,
                           RegionService regions, MembershipService memberships, CurrentActorService currentActor,
                           PermissionService permissions, HistoryService history, ProgressTracker tracker,
                           ApplicationEventPublisher events, AppProperties properties, Clock clock) {
        this.issues = issues;
        this.requests = requests;
        this.renderer = renderer;
        this.wallets = wallets;
        this.users = users;
        this.regions = regions;
        this.memberships = memberships;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.tracker = tracker;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------ validity

    /** Latest issue, else {@code last_passport_at} + validity; null when the user never had a passport. */
    @Transactional(readOnly = true)
    public Validity validity(User user) {
        Optional<PassportIssue> latest = issues.findFirstByUserIdOrderByValidUntilDescCreatedAtDesc(user.getId());
        if (latest.isPresent()) {
            return new Validity(latest.get().getValidFrom(), latest.get().getValidUntil());
        }
        if (user.getLastPassportAt() == null) {
            return null;
        }
        LocalDate from = user.getLastPassportAt().atZone(zoneOf(user)).toLocalDate();
        return new Validity(from, until(from));
    }

    LocalDate until(LocalDate from) {
        return from.plusYears(properties.getVerification().getPassportValidityYears());
    }

    /** Valid while today is before the end of validity. */
    public static boolean isValid(Validity validity, LocalDate today) {
        return validity != null && validity.validUntil().isAfter(today);
    }

    LocalDate today(User user) {
        return clock.instant().atZone(zoneOf(user)).toLocalDate();
    }

    // ------------------------------------------------------------------------------------------ own

    @Transactional(readOnly = true)
    public StatusView myStatus() {
        User user = users.require(currentActor.requireUserId());
        Validity validity = validity(user);
        return new StatusView(user.isVerified(), user.getLastPassportAt(),
                validity == null ? null : validity.validFrom(), validity == null ? null : validity.validUntil(),
                isValid(validity, today(user)), requests.existsByUserIdAndFulfilledAtIsNull(user.getId()),
                availability(user, wallets.appleEnabled()), availability(user, wallets.googleEnabled()));
    }

    private static Availability availability(User user, boolean configured) {
        if (!user.isVerified()) {
            return Availability.NOT_VERIFIED;
        }
        return configured ? Availability.AVAILABLE : Availability.NOT_CONFIGURED;
    }

    /** @throws ApiException 403 NOT_VERIFIED, 409 PASSPORT_NOT_VALID */
    @Transactional
    public byte[] downloadMine() {
        User user = requireVerifiedAndValid();
        byte[] pdf = renderer.render(List.of(card(user, validity(user))), Paper.CARD);
        history.record(history.event(PassportAction.PASSPORT_GENERATED, user.getId()).build());
        return pdf;
    }

    /** The current user, verified and with a valid passport. */
    User requireVerifiedAndValid() {
        User user = users.require(currentActor.requireUserId());
        if (!user.isVerified()) {
            throw ApiException.forbidden(ApiErrorCode.NOT_VERIFIED);
        }
        if (!isValid(validity(user), today(user))) {
            throw ApiException.conflict(ApiErrorCode.PASSPORT_NOT_VALID);
        }
        return user;
    }

    /** @throws ApiException 403 NOT_VERIFIED, 409 PASSPORT_REQUEST_PENDING */
    @Transactional
    public StatusView request() {
        User user = users.require(currentActor.requireUserId());
        if (!user.isVerified()) {
            throw ApiException.forbidden(ApiErrorCode.NOT_VERIFIED);
        }
        if (requests.existsByUserIdAndFulfilledAtIsNull(user.getId())) {
            throw ApiException.conflict(ApiErrorCode.PASSPORT_REQUEST_PENDING);
        }
        PassportRequest request = new PassportRequest();
        request.setUserId(user.getId());
        request.setRequestedAt(clock.instant());
        requests.save(request);
        history.record(history.event(PassportAction.PASSPORT_REQUESTED, user.getId())
                .region(user.getHomeRegionId()).build());
        if (user.getHomeRegionId() != null) {
            events.publishEvent(new PassportRequested(user.getId(), user.getHomeRegionId()));
        }
        return myStatus();
    }

    // ------------------------------------------------------------------------------------- ambassadors

    /** Requests of the region's members (open first). @throws ApiException 404, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public List<RequestView> requests(UUID regionId, boolean includeFulfilled) {
        regions.require(regionId);
        permissions.requireAmbassadorOrOrga(currentActor.requirePrincipal(), regionId);
        List<UUID> members = memberships.membersOf(regionId);
        if (members.isEmpty()) {
            return List.of();
        }
        List<PassportRequest> list = requests.findByUserIdInOrderByRequestedAtDesc(members).stream()
                .filter(r -> includeFulfilled || r.getFulfilledAt() == null).toList();
        Set<UUID> ids = new LinkedHashSet<>();
        list.forEach(r -> {
            ids.add(r.getUserId());
            if (r.getFulfilledBy() != null) {
                ids.add(r.getFulfilledBy());
            }
        });
        Map<UUID, User> byId = users.findAll(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().sorted(Comparator.comparing((PassportRequest r) -> r.getFulfilledAt() != null))
                .map(r -> new RequestView(r, byId.get(r.getUserId()),
                        r.getFulfilledBy() == null ? null : byId.get(r.getFulfilledBy()))).toList();
    }

    /**
     * Generates passports for members of the region; the PDF (when asked for) is the job result.
     *
     * @return the job id
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED / USERS_NOT_IN_REGION (args.userIds),
     *                      400 VALIDATION_FAILED
     */
    @Transactional
    public UUID createForRegion(UUID regionId, BatchInput input) {
        Region region = regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        permissions.requireAmbassadorOrOrga(actor, regionId);
        if (input.userIds() == null || input.userIds().isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<UUID> ids = input.userIds().stream().distinct().toList();
        Set<UUID> members = Set.copyOf(memberships.membersOf(regionId));
        List<UUID> outside = ids.stream().filter(id -> !members.contains(id)).toList();
        if (!outside.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.USERS_NOT_IN_REGION, List.of(),
                    Map.of("userIds", outside.stream().map(UUID::toString).collect(Collectors.joining(","))));
        }
        Map<UUID, User> byId = users.findAll(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<User> targets = ids.stream().map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
        UUID jobId = tracker.start(actor.userId(), targets.size());
        List<Card> cards = new ArrayList<>();
        int done = 0;
        for (User user : targets) {
            Validity validity = issueFor(user, region, actor.userId(), input);
            cards.add(card(user, validity));
            history.record(history.event(PassportAction.PASSPORT_GENERATED_BY_AMBASSADOR, user.getId())
                    .secondarySubject(regionId).region(regionId)
                    .metadata("validFrom", validity.validFrom().toString())
                    .metadata("validUntil", validity.validUntil().toString())
                    .metadata("renew", input.renew()).build());
            tracker.progress(jobId, ++done);
        }
        ProgressTracker.JobResult result = input.createPdf()
                ? new ProgressTracker.JobResult(renderer.render(cards, input.paper() == null ? Paper.A4
                : input.paper()), "application/pdf", "passports-" + region.getSlug() + ".pdf")
                : null;
        tracker.complete(jobId, result);
        if (input.notifyUsers()) {
            events.publishEvent(new PassportsCreated(targets.stream().map(User::getId).toList(), regionId));
        }
        return jobId;
    }

    /** Records the issue (renew / explicit start / reprint), updates last_passport_at and fulfils open requests. */
    private Validity issueFor(User user, Region region, UUID issuedBy, BatchInput input) {
        Validity current = validity(user);
        LocalDate today = today(user);
        LocalDate from;
        if (input.renew()) {
            from = today;
        } else if (input.validFrom() != null) {
            from = input.validFrom();
        } else {
            from = current == null ? today : current.validFrom();
        }
        LocalDate until = !input.renew() && input.validFrom() == null && current != null ? current.validUntil()
                : until(from);
        PassportIssue issue = new PassportIssue();
        issue.setUserId(user.getId());
        issue.setIssuedBy(issuedBy);
        issue.setValidFrom(from);
        issue.setValidUntil(until);
        issue.setKind(PassportKind.AMBASSADOR);
        issue.setRenew(input.renew());
        issue.setRegionId(region.getId());
        issues.save(issue);
        if (input.renew() || input.validFrom() != null || user.getLastPassportAt() == null) {
            users.setLastPassportAt(user.getId(), from.atStartOfDay(zoneOf(user)).toInstant());
        }
        for (PassportRequest request : requests.findByUserIdAndFulfilledAtIsNull(user.getId())) {
            request.setFulfilledAt(clock.instant());
            request.setFulfilledBy(issuedBy);
        }
        return new Validity(from, until);
    }

    Card card(User user, Validity validity) {
        String region = user.getHomeRegionId() == null ? null
                : regions.find(user.getHomeRegionId()).map(Region::getName).orElse(null);
        String name = user.getFirstName() != null && user.getLastName() != null
                ? user.getFirstName() + " " + user.getLastName() : user.getDisplayName();
        return new Card(name, user.getFoodsaverNumber(), region, validity.validFrom(), validity.validUntil(),
                qrUrl(user), user.getPreferredLanguage().getValue());
    }

    String qrUrl(User user) {
        return properties.getBaseUrl() + "/users/" + user.getId();
    }

    ZoneId zoneOf(User user) {
        return user.getHomeRegionId() == null ? ZoneId.of(properties.getRegion().getDefaultTimeZone())
                : regions.zoneOf(user.getHomeRegionId());
    }
}
