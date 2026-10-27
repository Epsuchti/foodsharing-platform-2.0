package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupComment;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.IntroPickupStoreAlias;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupStoreAliasRepository;
import network.foodsharing.intropickup.service.IntroPickupGuideService.CommentView;
import network.foodsharing.intropickup.service.IntroPickupViews.BookingView;
import network.foodsharing.intropickup.service.IntroPickupViews.PickupView;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EinAb administration of a region for CAN_MANAGE_USERS holders (plan §14.1 EA17–EA19): all EinAbs and bookings,
 * delete with a reason, historical EinAbs, counted / not counted, admin comments, guides (enable = grant of
 * CAN_GIVE_INTRO_PICKUPS) and the store aliases of the past import.
 */
@Service
public class IntroPickupAdminService {

    public record GuideSummaryView(User user, boolean active, boolean canManageUsers, int introPickupCount,
                                   Instant lastIntroPickupAt, Instant nextIntroPickupAt) {
    }

    public record HistoricalInput(IntroPickupCategory category, Instant startAt, String businessName,
                                  boolean visitFairteiler, String comment) {
    }

    /** After commit: mail to the guide when an admin deleted the EinAb. */
    public record IntroPickupDeletedByAdmin(UUID introPickupId, UUID guideId, String reason) {
    }

    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupCommentRepository comments;
    private final IntroPickupStoreAliasRepository aliases;
    private final IntroPickupViews views;
    private final IntroPickupGuideService guides;
    private final RegionService regions;
    private final RegionMembersService members;
    private final RegionPermissionResolver resolver;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;

    public IntroPickupAdminService(IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                                   IntroPickupCommentRepository comments, IntroPickupStoreAliasRepository aliases,
                                   IntroPickupViews views, IntroPickupGuideService guides, RegionService regions,
                                   RegionMembersService members, RegionPermissionResolver resolver,
                                   UserService users, CurrentActorService currentActor,
                                   PermissionService permissions, HistoryService history,
                                   ApplicationEventPublisher events) {
        this.pickups = pickups;
        this.slots = slots;
        this.comments = comments;
        this.aliases = aliases;
        this.views = views;
        this.guides = guides;
        this.regions = regions;
        this.members = members;
        this.resolver = resolver;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<PickupView> pickups(UUID regionId, boolean past, Integer page, Integer size) {
        requireAdmin(regionId);
        Instant now = views.now();
        List<IntroPickup> all = pickups.findByRegion(regionId).stream()
                .filter(p -> p.getStartAt().isBefore(now) == past)
                .sorted(past ? Comparator.comparing(IntroPickup::getStartAt).reversed()
                        : Comparator.comparing(IntroPickup::getStartAt)).toList();
        Page<IntroPickup> paged = IntroPickupViews.page(all, page, size);
        List<PickupView> content = views.pickups(paged.getContent());
        return paged.map(p -> content.stream().filter(v -> v.pickup().getId().equals(p.getId())).findFirst()
                .orElseThrow());
    }

    /**
     * @throws ApiException 404 REGION_NOT_FOUND / INTRO_PICKUP_NOT_FOUND, 403, 400 VALIDATION_FAILED (reason),
     *                      409 PAST_INTRO_PICKUP_NOT_DELETABLE / BOOKED_SLOTS_PREVENT_DELETE
     */
    @Transactional
    public void delete(UUID regionId, UUID introPickupId, String reason) {
        requireAdmin(regionId);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        IntroPickup pickup = views.requirePickup(introPickupId);
        if (!pickup.getRegionId().equals(regionId)) {
            throw ApiException.notFound(ApiErrorCode.INTRO_PICKUP_NOT_FOUND);
        }
        guides.softDelete(pickup);
        history.record(history.event(IntroPickupAction.DELETED_BY_ADMIN, pickup.getId()).region(regionId)
                .reason(reason.strip()).build());
        if (!pickup.getGuideId().equals(currentActor.requireUserId())) {
            events.publishEvent(new IntroPickupDeletedByAdmin(pickup.getId(), pickup.getGuideId(), reason.strip()));
        }
    }

    /**
     * Records an EinAb the user did before the platform (a DONE booking with the admin as guide); never notifies.
     *
     * @throws ApiException 404 REGION_NOT_FOUND / USER_NOT_FOUND, 403, 400 HISTORICAL_INTRO_PICKUP_NOT_IN_PAST /
     *                      VALIDATION_FAILED, 409 NOT_REGION_MEMBER
     */
    @Transactional
    public BookingView createHistorical(UUID regionId, UUID userId, HistoricalInput input) {
        requireAdmin(regionId);
        User user = users.require(userId);
        if (!resolver.isMember(user.getId(), regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_REGION_MEMBER);
        }
        if (input.startAt() == null || !input.startAt().isBefore(views.now())) {
            throw ApiException.badRequest(ApiErrorCode.HISTORICAL_INTRO_PICKUP_NOT_IN_PAST);
        }
        if (input.category() == null || input.businessName() == null || input.businessName().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        UUID adminId = currentActor.requireUserId();
        IntroPickupSlot slot = createPastBooking(regionId, adminId, user.getId(), input.category(), input.startAt(),
                input.businessName().strip(), input.visitFairteiler(), false, false);
        if (input.comment() != null && !input.comment().isBlank()) {
            addComment(slot, adminId, input.comment(), true);
        }
        history.record(history.event(IntroPickupAction.HISTORICAL_CREATED, slot.getIntroPickupId())
                .secondarySubject(userId).region(regionId).build());
        return views.booking(slot);
    }

    /** A past EinAb with one booking (historical entry or CSV import); no history, no events. */
    IntroPickupSlot createPastBooking(UUID regionId, UUID guideId, UUID userId, IntroPickupCategory category,
                                      Instant startAt, String businessName, boolean visitFairteiler,
                                      boolean imported, boolean notCounted) {
        IntroPickup pickup = new IntroPickup();
        pickup.setRegionId(regionId);
        pickup.setGuideId(guideId);
        pickup.setCategory(category);
        pickup.setStartAt(startAt);
        pickup.setBusinessName(businessName);
        pickup.setVisitFairteiler(visitFairteiler && category != IntroPickupCategory.ONLINE);
        pickup.setSlotCount(1);
        pickup.setImported(imported);
        pickups.save(pickup);
        IntroPickupSlot slot = new IntroPickupSlot();
        slot.setIntroPickupId(pickup.getId());
        slot.setGuideId(guideId);
        slot.setBookingUserId(userId);
        // Same state as the admin "do not count" action: CANCELLED keeps the user and the comment.
        slot.setStatus(notCounted ? SlotStatus.CANCELLED : SlotStatus.DONE);
        slot.setBookedAt(startAt);
        slot.setDoneAt(notCounted ? null : startAt);
        return slots.save(slot);
    }

    IntroPickupComment addComment(IntroPickupSlot slot, UUID authorId, String text, boolean admin) {
        IntroPickupComment comment = new IntroPickupComment();
        comment.setSlotId(slot.getId());
        comment.setBookingUserId(slot.getBookingUserId());
        comment.setAuthorId(authorId);
        comment.setAdminComment(admin);
        String value = text.strip();
        comment.setText(value.length() > 4000 ? value.substring(0, 4000) : value);
        return comments.save(comment);
    }

    @Transactional(readOnly = true)
    public Page<BookingView> bookings(UUID regionId, boolean past, UUID userId, Integer page, Integer size) {
        requireAdmin(regionId);
        Instant now = views.now();
        List<BookingView> all = views.bookings(slots.findBookingsInRegion(regionId)).stream()
                .filter(b -> userId == null || userId.equals(b.slot().slot().getBookingUserId()))
                .filter(b -> b.pickup().pickup().getStartAt().isBefore(now) == past)
                .sorted(past ? Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt()).reversed()
                        : Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt()))
                .toList();
        return IntroPickupViews.page(all, page, size);
    }

    /**
     * Counted ↔ not counted for a past booking (reference {@code AdminService.setCounted}).
     *
     * @throws ApiException 404 SLOT_NOT_FOUND, 403, 409 BOOKING_NOT_FOUND / ONLY_PAST_APPOINTMENTS_COUNTABLE /
     *                      ONLY_CANCELLED_APPOINTMENTS_COUNTABLE / ONLY_COUNTED_APPOINTMENTS_NOT_COUNTABLE
     */
    @Transactional
    public BookingView setCounted(UUID slotId, boolean counted, String reason) {
        IntroPickupSlot slot = views.requireSlot(slotId, true);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        requireAdmin(pickup.getRegionId());
        if (slot.getBookingUserId() == null) {
            throw ApiException.conflict(ApiErrorCode.BOOKING_NOT_FOUND);
        }
        if (!pickup.getStartAt().isBefore(views.now())) {
            throw ApiException.conflict(ApiErrorCode.ONLY_PAST_APPOINTMENTS_COUNTABLE);
        }
        if (counted) {
            if (slot.getStatus() != SlotStatus.CANCELLED) {
                throw ApiException.conflict(ApiErrorCode.ONLY_CANCELLED_APPOINTMENTS_COUNTABLE);
            }
            slot.setStatus(SlotStatus.BOOKED);
        } else {
            if (slot.getStatus() != SlotStatus.BOOKED && slot.getStatus() != SlotStatus.DONE) {
                throw ApiException.conflict(ApiErrorCode.ONLY_COUNTED_APPOINTMENTS_NOT_COUNTABLE);
            }
            slot.setStatus(SlotStatus.CANCELLED);
        }
        slot.setDoneAt(null);
        history.record(history.event(counted ? IntroPickupAction.COUNTED : IntroPickupAction.NOT_COUNTED,
                        pickup.getId()).secondarySubject(slot.getBookingUserId()).region(pickup.getRegionId())
                .reason(reason == null || reason.isBlank() ? null : reason.strip()).build());
        return views.booking(slot);
    }

    /** @throws ApiException 404 SLOT_NOT_FOUND, 403, 409 BOOKING_NOT_FOUND */
    @Transactional
    public CommentView addAdminComment(UUID slotId, String text) {
        IntroPickupSlot slot = views.requireSlot(slotId, false);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        requireAdmin(pickup.getRegionId());
        IntroPickupComment comment = guides.saveComment(slot, text, true);
        history.record(history.event(IntroPickupAction.COMMENT_ADDED, pickup.getId())
                .secondarySubject(slot.getBookingUserId()).region(pickup.getRegionId())
                .metadata("admin", true).build());
        return new CommentView(comment, currentActor.requireUser());
    }

    /** Guides of the region: current holders of CAN_GIVE_INTRO_PICKUPS and everybody who gave EinAbs here. */
    @Transactional(readOnly = true)
    public Page<GuideSummaryView> guides(UUID regionId, Integer page, Integer size) {
        requireAdmin(regionId);
        Set<UUID> active = resolver.holders(Permission.CAN_GIVE_INTRO_PICKUPS, regionId);
        Set<UUID> managers = resolver.holders(Permission.CAN_MANAGE_USERS, regionId);
        List<IntroPickup> all = pickups.findByRegion(regionId);
        Set<UUID> ids = new HashSet<>(active);
        all.forEach(p -> ids.add(p.getGuideId()));
        Map<UUID, List<IntroPickup>> byGuide = new HashMap<>();
        all.forEach(p -> byGuide.computeIfAbsent(p.getGuideId(), id -> new ArrayList<>()).add(p));
        Instant now = views.now();
        List<GuideSummaryView> result = users.findAll(ids).stream().map(user -> {
            List<IntroPickup> own = byGuide.getOrDefault(user.getId(), List.of());
            Instant last = own.stream().map(IntroPickup::getStartAt).filter(t -> t.isBefore(now))
                    .max(Comparator.naturalOrder()).orElse(null);
            Instant next = own.stream().map(IntroPickup::getStartAt).filter(t -> !t.isBefore(now))
                    .min(Comparator.naturalOrder()).orElse(null);
            return new GuideSummaryView(user, user.isActive() && active.contains(user.getId()),
                    managers.contains(user.getId()), own.size(), last, next);
        }).sorted(Comparator.comparing(GuideSummaryView::active).reversed()
                .thenComparing(v -> v.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER)).toList();
        return IntroPickupViews.page(result, page, size);
    }

    /**
     * Enables / disables a guide (grant / revoke of CAN_GIVE_INTRO_PICKUPS in the region).
     *
     * @throws ApiException 404 REGION_NOT_FOUND / USER_NOT_FOUND, 403, 409 NOT_REGION_MEMBER
     */
    @Transactional
    public GuideSummaryView setGuideEnabled(UUID regionId, UUID userId, boolean enabled, String reason) {
        requireAdmin(regionId);
        User user = users.require(userId);
        if (enabled && !resolver.isMember(userId, regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_REGION_MEMBER);
        }
        if (enabled) {
            members.grant(regionId, userId, Permission.CAN_GIVE_INTRO_PICKUPS);
        } else {
            members.revoke(regionId, userId, Permission.CAN_GIVE_INTRO_PICKUPS);
        }
        history.record(history.event(enabled ? IntroPickupAction.GUIDE_ENABLED : IntroPickupAction.GUIDE_DISABLED,
                        userId).secondarySubject(userId).region(regionId)
                .reason(reason == null || reason.isBlank() ? null : reason.strip()).build());
        List<IntroPickup> own = pickups.findByRegion(regionId).stream()
                .filter(p -> p.getGuideId().equals(userId)).toList();
        Instant now = views.now();
        return new GuideSummaryView(user,
                user.isActive() && resolver.hasRegionPermission(userId, Permission.CAN_GIVE_INTRO_PICKUPS, regionId),
                resolver.hasRegionPermission(userId, Permission.CAN_MANAGE_USERS, regionId), own.size(),
                own.stream().map(IntroPickup::getStartAt).filter(t -> t.isBefore(now)).max(Comparator.naturalOrder())
                        .orElse(null),
                own.stream().map(IntroPickup::getStartAt).filter(t -> !t.isBefore(now)).min(Comparator.naturalOrder())
                        .orElse(null));
    }

    // ------------------------------------------------------------------------------------ store aliases

    @Transactional(readOnly = true)
    public List<IntroPickupStoreAlias> aliases(UUID regionId) {
        requireAdmin(regionId);
        return aliases.findByRegionIdOrderByAliasKeyAsc(regionId);
    }

    /** @throws ApiException 409 STORE_ALIAS_EXISTS, 400 VALIDATION_FAILED */
    @Transactional
    public IntroPickupStoreAlias createAlias(UUID regionId, String aliasKey, String storeName) {
        requireAdmin(regionId);
        IntroPickupStoreAlias alias = new IntroPickupStoreAlias();
        alias.setRegionId(regionId);
        fillAlias(alias, aliasKey, storeName);
        aliases.save(alias);
        history.record(history.event(IntroPickupAction.STORE_ALIAS_CHANGED, alias.getId()).region(regionId)
                .metadata("aliasKey", alias.getAliasKey()).metadata("storeName", alias.getStoreName())
                .metadata("operation", "created").build());
        return alias;
    }

    /** @throws ApiException 404 STORE_ALIAS_NOT_FOUND, 409 STORE_ALIAS_EXISTS */
    @Transactional
    public IntroPickupStoreAlias updateAlias(UUID regionId, UUID aliasId, String aliasKey, String storeName) {
        requireAdmin(regionId);
        IntroPickupStoreAlias alias = aliases.findByIdAndRegionId(aliasId, regionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.STORE_ALIAS_NOT_FOUND));
        fillAlias(alias, aliasKey, storeName);
        history.record(history.event(IntroPickupAction.STORE_ALIAS_CHANGED, alias.getId()).region(regionId)
                .metadata("aliasKey", alias.getAliasKey()).metadata("storeName", alias.getStoreName())
                .metadata("operation", "updated").build());
        return alias;
    }

    /** @throws ApiException 404 STORE_ALIAS_NOT_FOUND */
    @Transactional
    public void deleteAlias(UUID regionId, UUID aliasId) {
        requireAdmin(regionId);
        IntroPickupStoreAlias alias = aliases.findByIdAndRegionId(aliasId, regionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.STORE_ALIAS_NOT_FOUND));
        aliases.delete(alias);
        history.record(history.event(IntroPickupAction.STORE_ALIAS_CHANGED, alias.getId()).region(regionId)
                .metadata("aliasKey", alias.getAliasKey()).metadata("operation", "deleted").build());
    }

    private void fillAlias(IntroPickupStoreAlias alias, String aliasKey, String storeName) {
        String key = PastImportService.storeKey(aliasKey == null ? "" : aliasKey);
        if (key.isEmpty() || storeName == null || storeName.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        aliases.findByRegionIdAndAliasKey(alias.getRegionId(), key)
                .filter(other -> !other.getId().equals(alias.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict(ApiErrorCode.STORE_ALIAS_EXISTS);
                });
        alias.setAliasKey(key);
        alias.setStoreName(storeName.strip());
    }

    void requireAdmin(UUID regionId) {
        regions.require(regionId);
        permissions.require(currentActor.requirePrincipal(), Permission.CAN_MANAGE_USERS, regionId);
    }
}
