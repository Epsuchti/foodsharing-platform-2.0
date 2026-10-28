package network.foodsharing.profile.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
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
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.profile.domain.Rating;
import network.foodsharing.profile.mapper.ProfileMapper;
import network.foodsharing.profile.service.BananaService;
import network.foodsharing.profile.service.BuddyService;
import network.foodsharing.profile.service.CommitmentsService;
import network.foodsharing.profile.service.ProfileService;
import network.foodsharing.profile.service.UserWallService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public profile, bananas, buddies, profile wall and commitments (docs/plan-m6.md B2). */
@RestController
public class ProfilesController implements ProfilesApi {

    private final ProfileService profiles;
    private final CommitmentsService commitments;
    private final BananaService bananas;
    private final BuddyService buddies;
    private final UserWallService wall;
    private final UserService users;
    private final ProfileMapper mapper;
    private final UserMapper userMapper;

    public ProfilesController(ProfileService profiles, CommitmentsService commitments, BananaService bananas,
                              BuddyService buddies, UserWallService wall, UserService users, ProfileMapper mapper,
                              UserMapper userMapper) {
        this.profiles = profiles;
        this.commitments = commitments;
        this.bananas = bananas;
        this.buddies = buddies;
        this.wall = wall;
        this.users = users;
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Override
    public ResponseEntity<PublicProfile> getPublicProfile(UUID userId) {
        return ResponseEntity.ok(mapper.toProfile(profiles.get(userId)));
    }

    @Override
    public ResponseEntity<Commitments> getUserCommitments(UUID userId, Integer weeks) {
        return ResponseEntity.ok(mapper.toCommitments(commitments.get(userId, weeks == null ? 4 : weeks)));
    }

    @Override
    public ResponseEntity<WallEntryPage> listUserWallPosts(UUID userId, Integer page, Integer size) {
        var result = wall.list(userId, page == null ? 0 : page, size == null ? 20 : size);
        Map<UUID, User> authors = usersById(result.getContent().stream().map(v -> v.post().getAuthorId()));
        return ResponseEntity.ok(new WallEntryPage()
                .items(result.getContent().stream()
                        .map(v -> mapper.toWallEntry(v, authors.get(v.post().getAuthorId()))).toList())
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<WallEntry> createUserWallPost(UUID userId, WallEntryCreate wallEntryCreate) {
        var view = wall.create(userId, wallEntryCreate.getBody());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toWallEntry(view, users.require(view.post().getAuthorId())));
    }

    @Override
    public ResponseEntity<Void> deleteUserWallPost(UUID userId, UUID postId) {
        wall.delete(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Banana>> listBananas(UUID userId, String direction) {
        boolean sent = "sent".equals(direction);
        List<Rating> list = bananas.list(userId, sent);
        Map<UUID, User> people = usersById(list.stream().flatMap(r -> Stream.of(r.getSenderId(), r.getRecipientId())));
        return ResponseEntity.ok(list.stream().filter(r -> people.containsKey(r.getSenderId())
                        && people.containsKey(r.getRecipientId()))
                .map(r -> mapper.toBanana(r, people.get(r.getSenderId()), people.get(r.getRecipientId()))).toList());
    }

    @Override
    public ResponseEntity<Banana> giveBanana(UUID userId, BananaCreate bananaCreate) {
        Rating rating = bananas.give(userId, bananaCreate.getMessage());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toBanana(rating,
                users.require(rating.getSenderId()), users.require(rating.getRecipientId())));
    }

    @Override
    public ResponseEntity<Void> deleteBanana(UUID userId, UUID senderId) {
        bananas.delete(userId, senderId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<UserSummary>> listMyBuddies() {
        return ResponseEntity.ok(summaries(buddies.myBuddies()));
    }

    @Override
    public ResponseEntity<List<UserSummary>> listBuddyRequests() {
        return ResponseEntity.ok(summaries(buddies.incomingRequests()));
    }

    @Override
    public ResponseEntity<ProfileBuddy> requestBuddy(UUID userId) {
        return ResponseEntity.ok(new ProfileBuddy().status(ProfileMapper.relation(buddies.request(userId))));
    }

    @Override
    public ResponseEntity<Void> removeBuddy(UUID userId) {
        buddies.remove(userId);
        return ResponseEntity.noContent().build();
    }

    private List<UserSummary> summaries(List<User> list) {
        return list.stream().sorted(java.util.Comparator.comparing(User::getDisplayName))
                .map(userMapper::toSummary).toList();
    }

    private Map<UUID, User> usersById(Stream<UUID> ids) {
        Map<UUID, User> map = new HashMap<>();
        users.findAll(ids.distinct().toList()).forEach(u -> map.put(u.getId(), u));
        return map;
    }
}
