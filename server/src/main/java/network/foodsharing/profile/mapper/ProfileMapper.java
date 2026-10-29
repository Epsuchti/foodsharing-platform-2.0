package network.foodsharing.profile.mapper;

import java.util.List;
import network.foodsharing.api.model.Banana;
import network.foodsharing.api.model.BuddyRelation;
import network.foodsharing.api.model.CommitmentWeek;
import network.foodsharing.api.model.Commitments;
import network.foodsharing.api.model.ProfileAchievement;
import network.foodsharing.api.model.ProfileBanana;
import network.foodsharing.api.model.ProfileBuddy;
import network.foodsharing.api.model.ProfileChain;
import network.foodsharing.api.model.ProfilePermissions;
import network.foodsharing.api.model.ProfileRegion;
import network.foodsharing.api.model.ProfileStats;
import network.foodsharing.api.model.ProfileStore;
import network.foodsharing.api.model.PublicProfile;
import network.foodsharing.api.model.WallEntry;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.profile.domain.Rating;
import network.foodsharing.profile.service.BuddyService;
import network.foodsharing.profile.service.CommitmentsService;
import network.foodsharing.profile.service.ProfileService;
import network.foodsharing.profile.service.UserWallService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** Profile read models to the generated models. */
@Component
public class ProfileMapper {

    private final UserMapper users;

    public ProfileMapper(UserMapper users) {
        this.users = users;
    }

    public PublicProfile toProfile(ProfileService.ProfileView view) {
        User user = view.user();
        var p = view.permissions();
        PublicProfile profile = new PublicProfile();
        profile.setUser(users.toSummary(user, view.withContact()));
        profile.setPhotoUrl(user.getAvatarUrl());
        profile.setAboutMe(user.getAboutMePublic());
        if (p.mayAdminister()) {
            profile.setAboutMeInternal(user.getAboutMeInternal());
        }
        profile.setRegions(view.regions().stream().map(ProfileMapper::region).toList());
        profile.setGroups(view.groups().stream().map(ProfileMapper::region).toList());
        if (view.stores() != null) {
            profile.setStores(view.stores().stream()
                    .map(s -> new ProfileStore().id(s.id()).name(s.name()).manager(s.manager())).toList());
        }
        ProfileService.Stats s = view.stats();
        profile.setStats(new ProfileStats().pickupCount(s.pickupCount()).kgSaved(s.kgSaved())
                .postCount(s.postCount()).bananaCount(s.bananaCount()).buddyCount(s.buddyCount())
                .basketsSharedCount(s.basketsShared()));
        profile.setBanana(new ProfileBanana().count(s.bananaCount()).givenByMe(view.bananaGivenByMe())
                .mayGive(view.mayGiveBanana()));
        profile.setBuddy(new ProfileBuddy().status(relation(view.buddy())));
        profile.setAchievements(view.achievements().stream().map(a -> new ProfileAchievement().id(a.id())
                .name(a.name()).icon(a.icon()).awardedAt(TimeMapper.toOffset(a.awardedAt())).validUntil(a.validUntil())
                .regionName(a.regionName())).toList());
        profile.setKamChains(view.kamChains().stream().map(c -> new ProfileChain().id(c.id()).name(c.name())).toList());
        profile.setRegisteredAt(TimeMapper.toOffset(user.getRegisteredAt()));
        profile.setViolationCount(view.violationCount());
        profile.setNotesCount(view.notesCount());
        profile.setPermissions(new ProfilePermissions().mayEditProfile(p.mayEditProfile()).maySeeNotes(p.maySeeNotes())
                .mayReport(p.mayReport()).mayGiveBanana(view.mayGiveBanana()).maySeeHistory(p.maySeeHistory())
                .maySeeCommitments(p.maySeeCommitments()).mayAdminister(p.mayAdminister())
                .maySeePickups(p.maySeePickups()).mayPostWall(p.mayPostWall()));
        return profile;
    }

    public static BuddyRelation relation(BuddyService.Relation relation) {
        return BuddyRelation.fromValue(relation.name());
    }

    public Banana toBanana(Rating rating, User sender, User recipient) {
        return new Banana().sender(users.toSummary(sender)).recipient(users.toSummary(recipient))
                .message(rating.getMessage()).givenAt(TimeMapper.toOffset(rating.getCreatedAt()));
    }

    public WallEntry toWallEntry(UserWallService.PostView view, User author) {
        return new WallEntry().id(view.post().getId()).author(users.toSummary(author)).body(view.post().getBody())
                .createdAt(TimeMapper.toOffset(view.post().getCreatedAt())).mayDelete(view.mayDelete());
    }

    public Commitments toCommitments(CommitmentsService.Commitments commitments) {
        List<CommitmentWeek> weeks = commitments.weeks().stream().map(w -> new CommitmentWeek()
                .weekStart(w.weekStart()).pickups(w.pickups()).basketsOffered(w.basketsOffered())
                .basketsShared(w.basketsShared()).eventsCreated(w.eventsCreated())
                .eventsParticipated(w.eventsParticipated())).toList();
        return new Commitments().weeks(weeks).responsibleStores(commitments.responsibleStores());
    }

    private static ProfileRegion region(MembershipService.Unit unit) {
        return new ProfileRegion().id(unit.region().getId()).name(unit.region().getName())
                .type(unit.region().getType().name()).admin(unit.admin());
    }
}
