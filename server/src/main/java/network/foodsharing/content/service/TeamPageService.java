package network.foodsharing.content.service;

import java.util.Comparator;
import java.util.List;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public team page (C16, upstream {@code TeamController}): working groups flagged {@code team_page} with their
 * active members (display name, avatar, position description), members sorted by name.
 */
@Service
public class TeamPageService {

    public record Member(String displayName, String avatarUrl, String positionDescription) {
    }

    public record Group(Region group, List<Member> members) {
    }

    private final RegionService regions;
    private final UserService users;

    public TeamPageService(RegionService regions, UserService users) {
        this.regions = regions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Group> groups() {
        return regions.teamPageGroups().stream().map(group -> new Group(group,
                users.findActive(regions.activeMemberIds(group.getId())).stream()
                        .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .map(user -> new Member(user.getDisplayName(), user.getAvatarUrl(),
                                user.getPositionDescription()))
                        .toList()))
                .toList();
    }
}
