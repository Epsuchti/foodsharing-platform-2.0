package network.foodsharing.profile.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.profile.domain.Buddy;
import network.foodsharing.profile.repository.BuddyRepository;
import network.foodsharing.profile.repository.UserWallPostRepository;
import network.foodsharing.user.domain.BuddyStatus;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.ActivityContribution;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Activity feed source BUDDY_WALL (A19): posts on the profile walls of my confirmed buddies. */
@Component
@Transactional(readOnly = true)
public class BuddyWallActivity implements ActivityContribution {

    private final BuddyRepository buddies;
    private final UserWallPostRepository posts;
    private final UserService users;

    public BuddyWallActivity(BuddyRepository buddies, UserWallPostRepository posts, UserService users) {
        this.buddies = buddies;
        this.posts = posts;
        this.users = users;
    }

    @Override
    public String type() {
        return "BUDDY_WALL";
    }

    @Override
    public List<Item> latest(UUID userId, Instant before, int limit) {
        List<UUID> buddyIds = buddies.findByUserIdAndStatus(userId, BuddyStatus.CONFIRMED).stream()
                .map(Buddy::getBuddyId).toList();
        if (buddyIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> owners = users.findActive(buddyIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (owners.isEmpty()) {
            return List.of();
        }
        return posts.findByUserIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(owners.keySet(), before,
                        PageRequest.of(0, Math.max(limit, 1))).stream()
                .map(p -> {
                    String name = owners.get(p.getUserId()).getDisplayName();
                    return new Item(p.getId(), p.getCreatedAt(), name, p.getBody(), "/profile/" + p.getUserId(),
                            p.getAuthorId(), p.getUserId(), name);
                }).toList();
    }
}
