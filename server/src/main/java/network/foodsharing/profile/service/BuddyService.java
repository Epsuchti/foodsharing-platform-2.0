package network.foodsharing.profile.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.profile.domain.Buddy;
import network.foodsharing.profile.repository.BuddyRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.BuddyStatus;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buddies (upstream {@code fs_buddy}, A16). A request stores the requester's row as REQUESTED and bells the target;
 * a request by the target (or a cross request) confirms both directions. DELETE removes both directions (withdraw,
 * decline or end). {@code stat_buddy_count} = confirmed buddies.
 */
@Service
public class BuddyService {

    /** The relation from the current user's point of view. */
    public enum Relation {
        NONE,
        REQUESTED_BY_ME,
        REQUESTED_BY_THEM,
        CONFIRMED
    }

    private final BuddyRepository buddies;
    private final CurrentActorService actors;
    private final UserService users;
    private final ProfileNotifier notifier;
    private final HistoryService history;

    public BuddyService(BuddyRepository buddies, CurrentActorService actors, UserService users,
                        ProfileNotifier notifier, HistoryService history) {
        this.buddies = buddies;
        this.actors = actors;
        this.users = users;
        this.notifier = notifier;
        this.history = history;
    }

    /** Relation of the users {@code me} and {@code other}. */
    @Transactional(readOnly = true)
    public Relation relation(UUID me, UUID other) {
        Optional<Buddy> mine = buddies.findByUserIdAndBuddyId(me, other);
        if (mine.isPresent()) {
            return mine.get().getStatus() == BuddyStatus.CONFIRMED ? Relation.CONFIRMED : Relation.REQUESTED_BY_ME;
        }
        return buddies.findByUserIdAndBuddyId(other, me).map(theirs -> theirs.getStatus() == BuddyStatus.CONFIRMED
                ? Relation.CONFIRMED : Relation.REQUESTED_BY_THEM).orElse(Relation.NONE);
    }

    /** Confirmed buddies of the current user, by name. */
    @Transactional(readOnly = true)
    public List<User> myBuddies() {
        UUID me = actors.requireUserId();
        return users.findActive(buddies.findByUserIdAndStatus(me, BuddyStatus.CONFIRMED).stream()
                .map(Buddy::getBuddyId).toList());
    }

    /** Users who asked the current user and wait for an answer. */
    @Transactional(readOnly = true)
    public List<User> incomingRequests() {
        UUID me = actors.requireUserId();
        return users.findActive(buddies.findByBuddyIdAndStatus(me, BuddyStatus.REQUESTED).stream()
                .map(Buddy::getUserId).toList());
    }

    /** Request, or accept a pending request of the other user. @throws ApiException 400 BUDDY_SELF, 404 */
    @Transactional
    public Relation request(UUID userId) {
        UUID me = actors.requireUserId();
        if (me.equals(userId)) {
            throw ApiException.badRequest(ApiErrorCode.BUDDY_SELF);
        }
        User target = users.requireActive(userId);
        Relation relation = relation(me, userId);
        switch (relation) {
            case CONFIRMED, REQUESTED_BY_ME -> {
                return relation;
            }
            case REQUESTED_BY_THEM -> {
                Buddy theirs = buddies.findByUserIdAndBuddyId(userId, me).orElseThrow();
                theirs.setStatus(BuddyStatus.CONFIRMED);
                save(me, userId, BuddyStatus.CONFIRMED);
                buddies.flush();
                history.record(history.event(UserAction.BUDDY_CONFIRMED, me).metadata("buddyId", userId.toString())
                        .build());
                notifier.buddyRequestDone(userId, me);
                notifier.buddyRequestDone(me, userId);
                syncCounts(me, userId);
                return Relation.CONFIRMED;
            }
            default -> {
                save(me, userId, BuddyStatus.REQUESTED);
                history.record(history.event(UserAction.BUDDY_REQUESTED, me).metadata("buddyId", userId.toString())
                        .build());
                String name = users.require(me).getDisplayName();
                notifier.buddyRequested(me, name, target.getId());
                return Relation.REQUESTED_BY_ME;
            }
        }
    }

    /** Removes the relation in both directions (withdraw, decline, end); idempotent. @throws ApiException 404 */
    @Transactional
    public void remove(UUID userId) {
        UUID me = actors.requireUserId();
        users.require(userId);
        Optional<Buddy> mine = buddies.findByUserIdAndBuddyId(me, userId);
        Optional<Buddy> theirs = buddies.findByUserIdAndBuddyId(userId, me);
        if (mine.isEmpty() && theirs.isEmpty()) {
            return;
        }
        mine.ifPresent(buddies::delete);
        theirs.ifPresent(buddies::delete);
        buddies.flush();
        history.record(history.event(UserAction.BUDDY_REMOVED, me).metadata("buddyId", userId.toString()).build());
        notifier.buddyRequestDone(me, userId);
        notifier.buddyRequestDone(userId, me);
        syncCounts(me, userId);
    }

    private void save(UUID userId, UUID buddyId, BuddyStatus status) {
        Buddy row = buddies.findByUserIdAndBuddyId(userId, buddyId).orElseGet(Buddy::new);
        row.setUserId(userId);
        row.setBuddyId(buddyId);
        row.setStatus(status);
        buddies.save(row);
    }

    private void syncCounts(UUID a, UUID b) {
        users.setCommunityCounts(a, null, (int) buddies.countByUserIdAndStatus(a, BuddyStatus.CONFIRMED));
        users.setCommunityCounts(b, null, (int) buddies.countByUserIdAndStatus(b, BuddyStatus.CONFIRMED));
    }
}
