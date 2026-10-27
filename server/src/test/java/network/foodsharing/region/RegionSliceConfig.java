package network.foodsharing.region;

import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.service.ConferenceLinkService;
import network.foodsharing.region.service.GroupService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.PublicRegionService;
import network.foodsharing.region.service.RegionAdminService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.region.service.RegionStatisticsService;
import network.foodsharing.region.service.RegionTreeService;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.user.service.UserService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The region services for {@code @JpaSliceTest}s (no notification listener: events are only recorded). */
@TestConfiguration(proxyBeanMethods = false)
@Import({RegionClosureRepository.class, RegionService.class, RegionTreeService.class, RegionReadService.class,
        RegionMembersService.class, RegionOptionsService.class, RegionSettingsService.class,
        RegionStatisticsService.class, ConferenceLinkService.class, RegionAdminService.class,
        RegionsCsvImportService.class, GroupService.class, MembershipService.class, RegionPermissionResolver.class,
        PublicRegionService.class, UserService.class})
public class RegionSliceConfig {

    /** Fixtures bound to the slice's repositories. */
    public static RegionFixtures fixtures(org.springframework.context.ApplicationContext context) {
        return new RegionFixtures(
                context.getBean(network.foodsharing.region.repository.RegionRepository.class),
                context.getBean(RegionClosureRepository.class),
                context.getBean(network.foodsharing.region.repository.RegionMembershipRepository.class),
                context.getBean(network.foodsharing.region.repository.RegionAdminRepository.class),
                context.getBean(network.foodsharing.region.repository.RegionFunctionRepository.class),
                context.getBean(network.foodsharing.region.repository.RegionPermissionRepository.class),
                context.getBean(network.foodsharing.user.repository.UserRepository.class));
    }
}
