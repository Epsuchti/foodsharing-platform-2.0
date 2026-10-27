package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupComment;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.service.NewcomerProgressService.NextStep;
import network.foodsharing.intropickup.service.NewcomerProgressService.Stage;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the module for onboarding (step 6) and verification (READY queue): a user's EinAb progress in a
 * region, the date of the last EinAb and the guide comments on the user's bookings. No permission checks.
 */
@Service
public class IntroPickupProgressService {

    public record Summary(Stage stage, NextStep nextStep, int counted, int upcoming, int target,
                          boolean fairteilerRequired, boolean fairteilerDone, boolean fairteilerBooked,
                          boolean stalled, Instant lastActivityAt) {
    }

    public record GuideComment(UUID authorId, String authorName, String text, boolean adminComment,
                               Instant createdAt) {
    }

    private final NewcomerProgressService progress;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupRepository pickups;
    private final IntroPickupCommentRepository comments;
    private final IntroPickupViews views;

    public IntroPickupProgressService(NewcomerProgressService progress, IntroPickupSlotRepository slots,
                                      IntroPickupRepository pickups, IntroPickupCommentRepository comments,
                                      IntroPickupViews views) {
        this.progress = progress;
        this.slots = slots;
        this.pickups = pickups;
        this.comments = comments;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public Summary progressOf(UUID userId, UUID regionId) {
        NewcomerProgressService.Progress p = progress.progressOf(userId, regionId);
        return new Summary(p.stage(), p.nextStep(), p.counted(), p.upcoming(), p.target(), p.fairteilerRequired(),
                p.fairteilerDone(), p.fairteilerBooked(), p.stalled(), p.lastActivityAt());
    }

    /** Start of the user's latest past counted EinAb (any region), or null. */
    @Transactional(readOnly = true)
    public Instant lastIntroPickupAt(UUID userId) {
        Instant now = views.now();
        var booked = slots.findBookingsOfUser(userId);
        if (booked.isEmpty()) {
            return null;
        }
        Map<UUID, network.foodsharing.intropickup.domain.IntroPickup> byId = new java.util.HashMap<>();
        pickups.findAllIn(booked.stream().map(s -> s.getIntroPickupId()).distinct().toList())
                .forEach(p -> byId.put(p.getId(), p));
        return booked.stream().filter(s -> byId.containsKey(s.getIntroPickupId()))
                .filter(s -> IntroPickupViews.counts(s, byId.get(s.getIntroPickupId()), now))
                .map(s -> byId.get(s.getIntroPickupId()).getStartAt()).max(Comparator.naturalOrder()).orElse(null);
    }

    /** Guide and admin comments on the user's bookings, oldest first. */
    @Transactional(readOnly = true)
    public List<GuideComment> commentsOn(UUID userId) {
        List<IntroPickupComment> list = comments.findByBookingUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, User> authors = views.usersById(list.stream().map(IntroPickupComment::getAuthorId)
                .filter(Objects::nonNull).toList());
        return list.stream().map(c -> {
            User author = c.getAuthorId() == null ? null : authors.get(c.getAuthorId());
            return new GuideComment(c.getAuthorId(), author == null ? null : author.getDisplayName(), c.getText(),
                    c.isAdminComment(), c.getCreatedAt());
        }).toList();
    }
}
