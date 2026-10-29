package network.foodsharing.user.mapper;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ContactVisibility;
import network.foodsharing.api.model.Gender;
import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.UnitType;
import network.foodsharing.api.model.UserProfile;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.PhoneNumberValidator;
import network.foodsharing.user.service.ProfileChange;
import network.foodsharing.user.service.RegionDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** User entity ↔ generated models. Other modules reuse {@link #toSummary} for user cards. */
@Component
public class UserMapper {

    private static final java.time.ZoneId ZURICH = java.time.ZoneId.of("Europe/Zurich");

    private final ObjectProvider<RegionDirectory> regionDirectory;
    private final java.time.Clock clock;

    public UserMapper(ObjectProvider<RegionDirectory> regionDirectory, java.time.Clock clock) {
        this.regionDirectory = regionDirectory;
        this.clock = clock;
    }

    /** Sleeping today (A39, Europe/Zurich). */
    public boolean isSleeping(User user) {
        return user.isSleeping(java.time.LocalDate.ofInstant(clock.instant(), ZURICH));
    }

    public UserProfile toProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setEmailVerifiedAt(TimeMapper.toOffset(user.getEmailVerifiedAt()));
        profile.setDisplayName(user.getDisplayName());
        profile.setFirstName(user.getFirstName());
        profile.setLastName(user.getLastName());
        profile.setLanguage(user.getPreferredLanguage());
        profile.setTheme(user.getTheme());
        profile.setPickupRemindersEnabled(user.isPickupRemindersEnabled());
        profile.setRole(user.getRole());
        profile.setVerified(user.isVerified());
        profile.setAvatarUrl(user.getAvatarUrl());
        profile.setRegisteredAt(TimeMapper.toOffset(user.getRegisteredAt()));
        profile.setLastLoginAt(TimeMapper.toOffset(user.getLastLoginAt()));
        profile.setActive(user.isActive());
        profile.setVerifiedAt(TimeMapper.toOffset(user.getVerifiedAt()));
        profile.setFoodsaverNumber(user.getFoodsaverNumber());
        profile.setQuizRole(user.getQuizRole());
        profile.setGender(user.getGender() == null ? null : Gender.fromValue(user.getGender().name()));
        profile.setDateOfBirth(user.getDateOfBirth());
        profile.setMobilePhone(user.getMobilePhone());
        profile.setLandlinePhone(user.getLandlinePhone());
        profile.setPhoneValid(user.getMobilePhone() != null && PhoneNumberValidator.isValid(user.getMobilePhone()));
        profile.setHomepage(user.getHomepage());
        profile.setContactVisibility(user.getContactVisibility() == null ? null
                : ContactVisibility.fromValue(user.getContactVisibility().name()));
        profile.setStreet(user.getStreet());
        profile.setPostcode(user.getPostcode());
        profile.setCity(user.getCity());
        profile.setCountry(user.getCountry());
        profile.setLatitude(user.getLatitude());
        profile.setLongitude(user.getLongitude());
        profile.setHomeRegion(regionRef(user.getHomeRegionId()));
        profile.setAboutMePublic(user.getAboutMePublic());
        profile.setAboutMeInternal(user.getAboutMeInternal());
        profile.setPositionDescription(user.getPositionDescription());
        profile.setNewsletterOptIn(user.isNewsletterOptIn());
        profile.setIdentityConfirmedAt(TimeMapper.toOffset(user.getIdentityConfirmedAt()));
        profile.setLastPassportAt(TimeMapper.toOffset(user.getLastPassportAt()));
        profile.setInfoMailMessage(user.isInfoMailMessage());
        profile.setMentionNotifications(user.isMentionNotifications());
        profile.setSleepStatus(network.foodsharing.api.model.SleepStatus.fromValue(user.getSleepStatus().name()));
        profile.setSleepFrom(user.getSleepFrom());
        profile.setSleepUntil(user.getSleepUntil());
        profile.setSleepMessage(user.getSleepMessage());
        profile.setEmailBouncedAt(TimeMapper.toOffset(user.getEmailBouncedAt()));
        return profile;
    }

    /** Admin view of a profile (G2): adds the archive flag and the deletion data. */
    public UserProfile toAdminProfile(User user) {
        UserProfile profile = toProfile(user);
        profile.setNoAutomaticDelete(user.isNoAutomaticDelete());
        profile.setDeletedAt(TimeMapper.toOffset(user.getDeletedAt()));
        profile.setDeleteReason(user.getDeleteReason());
        return profile;
    }

    /** User card without contact data. */
    /** Loads the home regions of a list of users in one query before their summaries are mapped. */
    public void prefetchHomeRegions(java.util.Collection<User> users) {
        RegionDirectory directory = regionDirectory.getIfAvailable();
        if (directory != null && !users.isEmpty()) {
            directory.prefetch(users.stream().map(User::getHomeRegionId).filter(java.util.Objects::nonNull).toList());
        }
    }

    public UserSummary toSummary(User user) {
        return toSummary(user, false);
    }

    /** User card; {@code withPhone} adds the mobile phone (only when the viewer may see it). */
    public UserSummary toSummary(User user, boolean withPhone) {
        UserSummary summary = new UserSummary();
        summary.setId(user.getId());
        summary.setDisplayName(user.getDisplayName());
        summary.setFirstName(user.getFirstName());
        summary.setAvatarUrl(user.getAvatarUrl());
        summary.setRole(user.getRole());
        summary.setVerified(user.isVerified());
        summary.setFoodsaverNumber(user.getFoodsaverNumber());
        summary.setHomeRegion(regionRef(user.getHomeRegionId()));
        boolean sleeping = isSleeping(user);
        summary.setSleepMode(sleeping);
        if (withPhone) {
            summary.setPhone(user.getMobilePhone());
            if (sleeping) {
                summary.setSleepMessage(user.getSleepMessage());
            }
        }
        return summary;
    }

    /** Admin note (A7); {@code author} may be null (system notes). */
    public network.foodsharing.api.model.UserNote toNote(network.foodsharing.user.domain.UserNote note,
                                                         boolean mayDelete, User author) {
        return new network.foodsharing.api.model.UserNote().id(note.getId()).body(note.getText())
                .author(author == null ? null : toSummary(author))
                .createdAt(TimeMapper.toOffset(note.getCreatedAt())).mayDelete(mayDelete);
    }

    public RegionRef regionRef(UUID regionId) {
        if (regionId == null) {
            return null;
        }
        RegionDirectory directory = regionDirectory.getIfAvailable();
        Optional<RegionDirectory.RegionInfo> info = directory == null ? Optional.empty() : directory.find(regionId);
        return info.map(region -> {
            RegionRef ref = new RegionRef();
            ref.setId(region.id());
            ref.setSlug(region.slug());
            ref.setName(region.name());
            ref.setType(UnitType.fromValue(region.type()));
            ref.setParentId(region.parentId());
            return ref;
        }).orElse(null);
    }

    public ProfileChange toChange(network.foodsharing.api.model.UserProfileUpdate update) {
        return new ProfileChange(update.getFirstName(), update.getLastName(), update.getDisplayName(),
                gender(update.getGender()), update.getDateOfBirth(), update.getMobilePhone(),
                update.getLandlinePhone(), update.getHomepage(),
                update.getContactVisibility() == null ? null
                        : network.foodsharing.user.domain.ContactVisibility.valueOf(update.getContactVisibility().name()),
                update.getStreet(), update.getPostcode(), update.getCity(), update.getCountry(), update.getLatitude(),
                update.getLongitude(), update.getAboutMePublic(), update.getAboutMeInternal(),
                update.getPositionDescription(), update.getNewsletterOptIn(), null);
    }

    public ProfileChange toChange(network.foodsharing.api.model.AdminUserProfileUpdate update) {
        return ProfileChange.admin(update.getFirstName(), update.getLastName(), update.getStreet(),
                update.getPostcode(), update.getCity(), update.getMobilePhone(), update.getDateOfBirth(),
                gender(update.getGender()), update.getHomeRegionId());
    }

    private static network.foodsharing.user.domain.Gender gender(Gender gender) {
        return gender == null ? null : network.foodsharing.user.domain.Gender.valueOf(gender.name());
    }
}
