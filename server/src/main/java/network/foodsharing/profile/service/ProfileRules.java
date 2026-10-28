package network.foodsharing.profile.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;

/**
 * Profile permission matrix (port of upstream {@code ProfilePermissions}, {@code BananaPermissions} and the profile
 * part of {@code WallPostPermissions}) as pure functions. {@code administers} = {@code mayAdministrateUser} (ORGA
 * always; ambassadors of one of the user's regions).
 */
public final class ProfileRules {

    /** Upstream {@code BananaMessage} minimum length. */
    public static final int BANANA_MIN_LENGTH = 100;

    /** What is known about the viewer relative to the profile owner. */
    public record Facts(boolean self, boolean orga, boolean administers, boolean shareStoreTeam,
                        boolean managesStoreOfUser, boolean reportButtonInCommonRegion) {
    }

    public record Permissions(boolean mayEditProfile, boolean maySeeNotes, boolean mayReport,
                              boolean maySeeHistory, boolean maySeeCommitments, boolean mayAdminister,
                              boolean maySeePickups, boolean maySeeStores) {
    }

    private ProfileRules() {
    }

    public static Permissions permissions(Facts f) {
        boolean admin = f.orga() || f.administers();
        return new Permissions(
                f.self() || admin,
                admin,
                !f.self() && f.reportButtonInCommonRegion(),
                admin,
                f.self() || admin || f.managesStoreOfUser(),
                admin,
                f.self() || admin,
                f.self() || admin || f.shareStoreTeam());
    }

    /**
     * Why a banana may not be given (empty = allowed): giver and recipient verified, not self, not yet given
     * (upstream {@code BananaPermissions::mayGiveBanana}).
     */
    public static Optional<ApiErrorCode> bananaDenied(UUID senderId, boolean senderVerified, UUID recipientId,
                                                      boolean recipientVerified, boolean alreadyGiven) {
        if (senderId.equals(recipientId) || !senderVerified || !recipientVerified) {
            return Optional.of(ApiErrorCode.BANANA_NOT_ALLOWED);
        }
        if (alreadyGiven) {
            return Optional.of(ApiErrorCode.BANANA_ALREADY_GIVEN);
        }
        return Optional.empty();
    }

    /** Message long enough (stripped, upstream minimum 100 characters). */
    public static boolean bananaMessageValid(String message) {
        return message != null && message.strip().length() >= BANANA_MIN_LENGTH;
    }

    /** Recipient, sender or ORGA may delete a banana. */
    public static boolean mayDeleteBanana(UUID actorId, boolean orga, UUID recipientId, UUID senderId) {
        return orga || actorId.equals(recipientId) || actorId.equals(senderId);
    }

    /** Upstream: only the owner writes on their profile wall (status updates). */
    public static boolean mayWriteWall(boolean self) {
        return self;
    }

    /** Delete a wall post: ORGA, the author, the wall owner or whoever administers the owner (A42). */
    public static boolean mayDeleteWallPost(UUID actorId, boolean orga, boolean administersOwner, UUID ownerId,
                                            UUID authorId) {
        return orga || administersOwner || actorId.equals(ownerId) || actorId.equals(authorId);
    }
}
