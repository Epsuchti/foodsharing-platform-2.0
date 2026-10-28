package network.foodsharing.profile.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.profile.domain.Rating;
import network.foodsharing.profile.repository.RatingRepository;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trust bananas (upstream {@code BananaPermissions}, G4); {@code stat_banana_count} stays in sync. */
@Service
public class BananaService {

    private final RatingRepository ratings;
    private final ProfilePermissionService access;
    private final ProfileNotifier notifier;
    private final UserService users;
    private final HistoryService history;

    public BananaService(RatingRepository ratings, ProfilePermissionService access, ProfileNotifier notifier,
                         UserService users, HistoryService history) {
        this.ratings = ratings;
        this.access = access;
        this.notifier = notifier;
        this.users = users;
        this.history = history;
    }

    /** {@code sent=false}: bananas the user received; {@code true}: bananas they gave. Newest first. */
    @Transactional(readOnly = true)
    public List<Rating> list(UUID userId, boolean sent) {
        access.basic(userId);
        return sent ? ratings.findBySenderIdOrderByCreatedAtDesc(userId)
                : ratings.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    /** May the current user give {@code userId} a banana (the profile's {@code mayGive}). */
    @Transactional(readOnly = true)
    public boolean mayGive(ProfilePermissionService.Context context) {
        boolean given = ratings.findByRecipientIdAndSenderId(context.target().getId(), context.actor().userId())
                .isPresent();
        return ProfileRules.bananaDenied(context.actor().userId(), context.viewer().isVerified(),
                context.target().getId(), context.target().isVerified(), given).isEmpty();
    }

    /**
     * @throws ApiException 404 USER_NOT_FOUND, 400 BANANA_MESSAGE_TOO_SHORT, 403 BANANA_NOT_ALLOWED,
     *                      409 BANANA_ALREADY_GIVEN
     */
    @Transactional
    public Rating give(UUID userId, String message) {
        ProfilePermissionService.Context context = access.basic(userId);
        UUID senderId = context.actor().userId();
        boolean given = ratings.findByRecipientIdAndSenderId(userId, senderId).isPresent();
        ProfileRules.bananaDenied(senderId, context.viewer().isVerified(), userId, context.target().isVerified(),
                given).ifPresent(code -> {
                    throw code == ApiErrorCode.BANANA_ALREADY_GIVEN ? ApiException.conflict(code)
                            : ApiException.forbidden(code);
                });
        if (!ProfileRules.bananaMessageValid(message)) {
            throw ApiException.badRequest(ApiErrorCode.BANANA_MESSAGE_TOO_SHORT);
        }
        Rating rating = new Rating();
        rating.setRecipientId(userId);
        rating.setSenderId(senderId);
        rating.setMessage(message.strip());
        rating = ratings.saveAndFlush(rating);
        history.record(history.event(UserAction.BANANA_GIVEN, userId).metadata("senderId", senderId.toString())
                .build());
        users.setCommunityCounts(userId, (int) ratings.countByRecipientId(userId), null);
        notifier.bananaGiven(userId, senderId, context.viewer().getDisplayName());
        return rating;
    }

    /** @throws ApiException 404 USER_NOT_FOUND / BANANA_NOT_FOUND, 403 unless recipient, sender or ORGA */
    @Transactional
    public void delete(UUID userId, UUID senderId) {
        ProfilePermissionService.Context context = access.basic(userId);
        Rating rating = ratings.findByRecipientIdAndSenderId(userId, senderId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BANANA_NOT_FOUND));
        ProfilePermissionService.require(ProfileRules.mayDeleteBanana(context.actor().userId(), context.orga(),
                userId, senderId));
        ratings.delete(rating);
        ratings.flush();
        history.record(history.event(UserAction.BANANA_DELETED, userId).metadata("senderId", senderId.toString())
                .build());
        users.setCommunityCounts(userId, (int) ratings.countByRecipientId(userId), null);
        notifier.bananaDeleted(userId, senderId);
    }
}
