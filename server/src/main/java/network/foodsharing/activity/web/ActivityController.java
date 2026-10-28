package network.foodsharing.activity.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.activity.service.ActivityFeedService;
import network.foodsharing.api.ActivityApi;
import network.foodsharing.api.model.ActivityItem;
import network.foodsharing.api.model.ActivityPage;
import network.foodsharing.api.model.ActivitySource;
import network.foodsharing.api.model.ActivityType;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard activity feed (A19). */
@RestController
public class ActivityController implements ActivityApi {

    private final ActivityFeedService feed;
    private final UserService users;
    private final UserMapper userMapper;

    public ActivityController(ActivityFeedService feed, UserService users, UserMapper userMapper) {
        this.feed = feed;
        this.users = users;
        this.userMapper = userMapper;
    }

    @Override
    public ResponseEntity<ActivityPage> getActivityFeed(Integer page, Integer size) {
        var result = feed.feed(page == null ? 0 : page, size == null ? 20 : size);
        Set<UUID> authorIds = result.getContent().stream().map(ActivityFeedService.ActivityItem::authorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, UserSummary> authors = authorIds.isEmpty() ? Map.of() : users.findAll(authorIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toSummary, (a, b) -> a));
        List<ActivityItem> items = result.getContent().stream().map(i -> new ActivityItem()
                .type(ActivityType.fromValue(i.type().name())).at(TimeMapper.toOffset(i.at())).title(i.title())
                .excerpt(i.excerpt()).link(i.link()).author(i.authorId() == null ? null : authors.get(i.authorId()))
                .source(new ActivitySource().id(i.sourceId()).name(i.sourceName() == null ? "" : i.sourceName())))
                .toList();
        return ResponseEntity.ok(new ActivityPage().items(items).page(PageMapper.toPageMeta(result)));
    }
}
