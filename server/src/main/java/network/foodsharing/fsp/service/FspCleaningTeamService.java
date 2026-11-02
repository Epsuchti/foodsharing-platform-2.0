package network.foodsharing.fsp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The derived cleaning team of a Fairteiler (FS2, D1): its managers plus everyone with a cleaning sign-up, with the
 * confirmed past shifts of the last 12 months, the latest confirmed shift and the upcoming sign-ups.
 */
@Service
public class FspCleaningTeamService {

    /** One team member. {@code lastShiftAt} null = no confirmed past shift. */
    public record Member(User user, boolean manager, int shiftCount, int upcomingCount, Instant lastShiftAt) {
    }

    public record Team(List<Member> members, int totalShifts) {
    }

    private final FspCleaningScheduleService schedule;
    private final FspCleaningSignupRepository signups;
    private final FspFollowerRepository followers;
    private final FspPermissionService access;
    private final CurrentActorService actors;
    private final UserService users;
    private final Clock clock;

    public FspCleaningTeamService(FspCleaningScheduleService schedule, FspCleaningSignupRepository signups,
                                  FspFollowerRepository followers, FspPermissionService access,
                                  CurrentActorService actors, UserService users, Clock clock) {
        this.schedule = schedule;
        this.signups = signups;
        this.followers = followers;
        this.access = access;
        this.actors = actors;
        this.users = users;
        this.clock = clock;
    }

    /** @throws network.foodsharing.common.error.ApiException 404, 409 CLEANING_NOT_ENABLED */
    @Transactional(readOnly = true)
    public Team team(UUID fspId) {
        FoodSharePoint fsp = schedule.requireCleaning(fspId);
        access.requireVisible(actors.requirePrincipal(), fsp);
        Instant now = clock.instant();
        Instant since = now.atZone(ZoneId.of(fsp.getTimeZone())).minusMonths(12).toInstant();
        Set<UUID> managers = new LinkedHashSet<>();
        for (FspFollower row : followers.findByFspIdAndRole(fspId, FspFollowerRole.MANAGER)) {
            managers.add(row.getUserId());
        }
        Map<UUID, FspCleaningSignupRepository.TeamStatsRow> stats = new HashMap<>();
        signups.teamStats(fspId, since, now).forEach(row -> stats.put(row.getUserId(), row));
        Map<UUID, Integer> upcoming = new HashMap<>();
        for (Object[] row : signups.upcomingCounts(fspId, now)) {
            upcoming.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        Set<UUID> ids = new LinkedHashSet<>(managers);
        ids.addAll(stats.keySet());
        ids.addAll(upcoming.keySet());
        Map<UUID, User> byId = new HashMap<>();
        users.findActive(ids).forEach(u -> byId.put(u.getId(), u));
        List<Member> members = new ArrayList<>();
        int total = 0;
        for (UUID id : ids) {
            User user = byId.get(id);
            if (user == null) {
                continue;
            }
            FspCleaningSignupRepository.TeamStatsRow row = stats.get(id);
            int count = row == null || row.getRecentCount() == null ? 0 : row.getRecentCount().intValue();
            total += count;
            members.add(new Member(user, managers.contains(id), count, upcoming.getOrDefault(id, 0),
                    row == null ? null : row.getLastShiftAt()));
        }
        members.sort(ORDER);
        return new Team(List.copyOf(members), total);
    }

    static final Comparator<Member> ORDER = Comparator.comparing((Member m) -> !m.manager())
            .thenComparing(Member::shiftCount, Comparator.reverseOrder())
            .thenComparing(m -> m.user().getDisplayName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(m -> m.user().getId());
}
