package network.foodsharing.fsp.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Fsp;
import network.foodsharing.api.model.FspAddress;
import network.foodsharing.api.model.FspFollowing;
import network.foodsharing.api.model.FspPermissions;
import network.foodsharing.api.model.FspStatus;
import network.foodsharing.api.model.FspSummary;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.api.model.WallEntry;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.service.FspQueryService.FspSummaryView;
import network.foodsharing.fsp.service.FspQueryService.FspView;
import network.foodsharing.fsp.service.FspQueryService.PostView;
import network.foodsharing.fsp.service.FspRules;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

@Component
public class FspMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public FspMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public static String photoUrl(UUID uploadId) {
        return uploadId == null ? null : "/api/uploads/" + uploadId + "?size=md";
    }

    public List<FspSummary> toSummaries(List<FspSummaryView> views) {
        return views.stream().map(view -> new FspSummary().id(view.fsp().getId()).name(view.fsp().getName())
                .photoUrl(photoUrl(view.fsp().getPhotoUploadId())).city(view.fsp().getCity())
                .lat(view.fsp().getLat()).lon(view.fsp().getLon())
                .status(FspStatus.fromValue(view.fsp().getStatus().name())).followerCount(view.followerCount())
                .following(view.following())).toList();
    }

    public Fsp toFsp(FspView view) {
        FoodSharePoint fsp = view.fsp();
        Map<UUID, UserSummary> people = people(fsp.getCreatedByUserId() == null ? view.managerIds()
                : java.util.stream.Stream.concat(view.managerIds().stream(), java.util.stream.Stream.of(
                        fsp.getCreatedByUserId())).toList());
        FspRules.Facts facts = view.facts();
        FspFollower mine = view.myFollow();
        return new Fsp().id(fsp.getId()).name(fsp.getName()).description(fsp.getDescription())
                .photoUrl(photoUrl(fsp.getPhotoUploadId())).region(userMapper.regionRef(fsp.getRegionId()))
                .address(new FspAddress().street(fsp.getStreet()).postcode(fsp.getPostcode()).city(fsp.getCity()))
                .lat(fsp.getLat()).lon(fsp.getLon()).status(FspStatus.fromValue(fsp.getStatus().name()))
                .managers(view.managerIds().stream().map(people::get).filter(java.util.Objects::nonNull).toList())
                .creator(fsp.getCreatedByUserId() == null ? null : people.get(fsp.getCreatedByUserId()))
                .createdAt(TimeMapper.toOffset(fsp.getCreatedAt())).followerCount(view.followerCount())
                .following(toFollowing(mine))
                .permissions(new FspPermissions().mayEdit(FspRules.mayEdit(facts))
                        .mayDelete(FspRules.mayDelete(facts)).mayActivate(FspRules.mayActivate(facts))
                        .mayPost(FspRules.mayPost(facts)).mayFollow(FspRules.mayFollow(facts)));
    }

    public FspFollowing toFollowing(FspFollower row) {
        return row == null ? new FspFollowing().following(false).bell(false).mail(false)
                : new FspFollowing().following(true).bell(row.isBell()).mail(row.isMail());
    }

    public List<WallEntry> toWallEntries(List<PostView> views) {
        Map<UUID, UserSummary> people = people(views.stream().map(v -> v.post().getAuthorId()).toList());
        return views.stream().filter(v -> people.containsKey(v.post().getAuthorId()))
                .map(v -> toWallEntry(v, people.get(v.post().getAuthorId()))).toList();
    }

    public WallEntry toWallEntry(PostView view, UserSummary author) {
        return new WallEntry().id(view.post().getId()).author(author).body(view.post().getBody())
                .createdAt(TimeMapper.toOffset(view.post().getCreatedAt())).mayDelete(view.mayDelete());
    }

    public WallEntry toWallEntry(PostView view) {
        return toWallEntry(view, userMapper.toSummary(users.require(view.post().getAuthorId())));
    }

    private Map<UUID, UserSummary> people(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAll(Set.copyOf(ids)).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a));
    }
}
