package network.foodsharing.intropickup.service;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.quiz.service.GuideQuizEligibility;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.Permission;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q4 (decision 168): the pickup gate of the EINAB_GUIDE quiz. A user may try it with at least
 * {@code guideQuizMinPickups} confirmed past store pickups ({@link MemberPickupStats#pickupCount}); the threshold
 * comes from the home region, the global default (25) without one. Current holders of CAN_GIVE_INTRO_PICKUPS skip
 * the threshold (existing guides must pass the quiz within the grace period).
 */
@Service
public class GuideQuizGate implements GuideQuizEligibility {

    private final MemberPickupStats pickupStats;
    private final RegionService regions;
    private final RegionPermissionResolver resolver;
    private final UserService users;

    public GuideQuizGate(MemberPickupStats pickupStats, RegionService regions, RegionPermissionResolver resolver,
                         UserService users) {
        this.pickupStats = pickupStats;
        this.regions = regions;
        this.resolver = resolver;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Status of(UUID userId) {
        UUID home = users.require(userId).getHomeRegionId();
        int min = home == null ? RegionSettingsService.DEFAULT_GUIDE_QUIZ_MIN_PICKUPS
                : regions.find(home).map(Region::getGuideQuizMinPickups)
                        .orElse(RegionSettingsService.DEFAULT_GUIDE_QUIZ_MIN_PICKUPS);
        boolean guide = resolver.permissionsByRegion(userId).values().stream()
                .anyMatch((Set<Permission> granted) -> granted.contains(Permission.CAN_GIVE_INTRO_PICKUPS));
        return new Status(pickupStats.pickupCount(userId), min, guide);
    }
}
