package network.foodsharing.profile.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.profile.repository.RatingRepository;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.store.service.ProfileStoreInfo;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.ProfileAchievements;
import network.foodsharing.user.service.UserNoteService;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.service.UserViolationCounter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the public profile (A6, upstream {@code ProfileController}/{@code ProfilePermissions}). */
@Service
public class ProfileService {

    public record Stats(int pickupCount, double kgSaved, int postCount, int bananaCount, int buddyCount,
                        int basketsShared) {
    }

    /** Read model of {@code PublicProfile}; {@code stores} is null when the viewer may not see them. */
    public record ProfileView(User user, boolean withContact, List<MembershipService.Unit> regions,
                              List<MembershipService.Unit> groups, List<ProfileStoreInfo.TeamStore> stores,
                              Stats stats, boolean bananaGivenByMe, boolean mayGiveBanana,
                              BuddyService.Relation buddy, List<ProfileAchievements.Achievement> achievements,
                              List<ProfileStoreInfo.Chain> kamChains, ProfileRules.Permissions permissions,
                              Integer violationCount, Integer notesCount) {
    }

    private final ProfilePermissionService access;
    private final MembershipService memberships;
    private final ProfileStoreInfo stores;
    private final RatingRepository ratings;
    private final BananaService bananas;
    private final BuddyService buddies;
    private final UserService users;
    private final ObjectProvider<MemberPickupStats> pickupStats;
    private final ObjectProvider<ProfileAchievements> achievements;
    private final ObjectProvider<UserViolationCounter> reports;
    private final UserNoteService notes;

    public ProfileService(ProfilePermissionService access, MembershipService memberships, ProfileStoreInfo stores,
                          RatingRepository ratings, BananaService bananas, BuddyService buddies, UserService users,
                          ObjectProvider<MemberPickupStats> pickupStats,
                          ObjectProvider<ProfileAchievements> achievements,
                          ObjectProvider<UserViolationCounter> reports, UserNoteService notes) {
        this.reports = reports;
        this.notes = notes;
        this.access = access;
        this.memberships = memberships;
        this.stores = stores;
        this.ratings = ratings;
        this.bananas = bananas;
        this.buddies = buddies;
        this.users = users;
        this.pickupStats = pickupStats;
        this.achievements = achievements;
    }

    private Integer violationCount(UUID userId) {
        UserViolationCounter counter = reports.getIfAvailable();
        if (counter == null) {
            return null;
        }
        java.util.OptionalInt count = counter.visibleReportCount(userId);
        return count.isPresent() ? count.getAsInt() : null;
    }

    /** @throws network.foodsharing.common.error.ApiException 401, 404 USER_NOT_FOUND */
    @Transactional(readOnly = true)
    public ProfileView get(UUID userId) {
        ProfilePermissionService.Context context = access.context(userId);
        User user = context.target();
        UUID viewerId = context.actor().userId();
        List<MembershipService.Unit> units = memberships.units(userId).stream().filter(MembershipService.Unit::member)
                .toList();
        MemberPickupStats live = pickupStats.getIfAvailable();
        int pickups = Math.max(user.getStatPickupCount(), live == null ? 0 : live.pickupCount(userId));
        Stats stats = new Stats(pickups, user.getStatKgSaved(), user.getStatPostCount(),
                (int) ratings.countByRecipientId(userId), user.getStatBuddyCount(), user.getStatBasketsShared());
        boolean given = !context.facts().self() && ratings.findByRecipientIdAndSenderId(userId, viewerId).isPresent();
        List<ProfileAchievements.Achievement> awards = achievements.orderedStream()
                .flatMap(source -> source.visibleTo(viewerId, userId).stream()).toList();
        return new ProfileView(user, users.mayCurrentUserSeeContact(user),
                units.stream().filter(unit -> !unit.region().isGroup()).toList(),
                units.stream().filter(unit -> unit.region().isGroup()).toList(),
                context.permissions().maySeeStores() ? stores.teamStores(userId) : null,
                stats, given, bananas.mayGive(context),
                context.facts().self() ? BuddyService.Relation.NONE : buddies.relation(viewerId, userId),
                awards, stores.kamChains(userId), context.permissions(),
                context.facts().self() ? null : violationCount(userId),
                context.permissions().maySeeNotes() ? notes.countAdminNotes(userId) : null);
    }
}
