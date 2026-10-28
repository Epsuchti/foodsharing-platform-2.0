package network.foodsharing.user.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.ProfilesApi;
import network.foodsharing.api.model.Banana;
import network.foodsharing.api.model.BananaCreate;
import network.foodsharing.api.model.Commitments;
import network.foodsharing.api.model.ProfileBuddy;
import network.foodsharing.api.model.PublicProfile;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.api.model.WallEntry;
import network.foodsharing.api.model.WallEntryCreate;
import network.foodsharing.api.model.WallEntryPage;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class ProfilesController implements ProfilesApi {

    @Override
    public ResponseEntity<WallEntry> createUserWallPost(UUID userId, WallEntryCreate wallEntryCreate) {
        throw ApiException.notImplemented("createUserWallPost");
    }

    @Override
    public ResponseEntity<Void> deleteBanana(UUID userId, UUID senderId) {
        throw ApiException.notImplemented("deleteBanana");
    }

    @Override
    public ResponseEntity<Void> deleteUserWallPost(UUID userId, UUID postId) {
        throw ApiException.notImplemented("deleteUserWallPost");
    }

    @Override
    public ResponseEntity<PublicProfile> getPublicProfile(UUID userId) {
        throw ApiException.notImplemented("getPublicProfile");
    }

    @Override
    public ResponseEntity<Commitments> getUserCommitments(UUID userId, Integer weeks) {
        throw ApiException.notImplemented("getUserCommitments");
    }

    @Override
    public ResponseEntity<Banana> giveBanana(UUID userId, BananaCreate bananaCreate) {
        throw ApiException.notImplemented("giveBanana");
    }

    @Override
    public ResponseEntity<List<Banana>> listBananas(UUID userId, String direction) {
        throw ApiException.notImplemented("listBananas");
    }

    @Override
    public ResponseEntity<List<UserSummary>> listBuddyRequests() {
        throw ApiException.notImplemented("listBuddyRequests");
    }

    @Override
    public ResponseEntity<List<UserSummary>> listMyBuddies() {
        throw ApiException.notImplemented("listMyBuddies");
    }

    @Override
    public ResponseEntity<WallEntryPage> listUserWallPosts(UUID userId, Integer page, Integer size) {
        throw ApiException.notImplemented("listUserWallPosts");
    }

    @Override
    public ResponseEntity<Void> removeBuddy(UUID userId) {
        throw ApiException.notImplemented("removeBuddy");
    }

    @Override
    public ResponseEntity<ProfileBuddy> requestBuddy(UUID userId) {
        throw ApiException.notImplemented("requestBuddy");
    }
}
