package network.foodsharing.region.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logged-out region data (plan §13.2 R4, §16): public region page, the region tree (regions with ambassadors are
 * "active", i.e. accept newcomers) and region suggestions. Working groups are never public here.
 */
@Service
@Transactional(readOnly = true)
public class PublicRegionService {

    public record PublicRegionView(Region region, Region parent, List<Region> children, List<String> ambassadorNames,
                                   String contactEmail, long memberCount,
                                   List<UpcomingIntroPickups.PublicIntroPickupView> nextIntroPickups,
                                   RegionOptionsService.Pin pin) {
    }

    public record Node(Region region, boolean active, List<Node> children) {
    }

    static final int NEXT_INTRO_PICKUPS = 5;

    private final RegionRepository regions;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionReadService reads;
    private final RegionOptionsService options;
    private final List<UpcomingIntroPickups> introPickups;
    private final AppProperties properties;

    public PublicRegionService(RegionRepository regions, RegionMembershipRepository memberships,
                               RegionAdminRepository admins, RegionReadService reads, RegionOptionsService options,
                               List<UpcomingIntroPickups> introPickups, AppProperties properties) {
        this.regions = regions;
        this.memberships = memberships;
        this.admins = admins;
        this.reads = reads;
        this.options = options;
        this.introPickups = introPickups;
        this.properties = properties;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND (also for working groups and inactive regions) */
    public PublicRegionView region(String slug) {
        Region region = regions.findBySlug(slug).filter(r -> !r.isGroup() && r.isActive())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        Region parent = region.getParentId() == null ? null : regions.findById(region.getParentId()).orElse(null);
        List<Region> children = regions.findByParentIdOrderBySortOrderAscNameAscIdAsc(region.getId()).stream()
                .filter(child -> !child.isGroup() && child.isActive()).toList();
        List<String> ambassadors = reads.ambassadorsOf(region.getId()).stream().map(User::getDisplayName).toList();
        String contact = region.getEmailName() == null ? null : region.getEmailName() + "@" + properties.getDomain();
        List<UpcomingIntroPickups.PublicIntroPickupView> next = introPickups.stream()
                .flatMap(source -> source.next(region.getId(), NEXT_INTRO_PICKUPS).stream())
                .limit(NEXT_INTRO_PICKUPS).toList();
        return new PublicRegionView(region, parent, children, ambassadors, contact,
                memberships.countByRegionIdAndActiveTrue(region.getId()), next,
                options.activePin(region.getId()).orElse(null));
    }

    /** The region tree below {@code rootSlug} (all roots when null). @throws ApiException 404 REGION_NOT_FOUND */
    public List<Node> tree(String rootSlug) {
        List<Region> all = regions.findAll().stream().filter(r -> !r.isGroup() && r.isActive()).toList();
        Set<UUID> withAmbassadors = admins.findAll().stream().map(admin -> admin.getRegionId())
                .collect(Collectors.toSet());
        Map<UUID, List<Region>> byParent = all.stream().filter(r -> r.getParentId() != null)
                .collect(Collectors.groupingBy(Region::getParentId));
        List<Region> roots;
        if (rootSlug == null || rootSlug.isBlank()) {
            roots = all.stream().filter(r -> r.getParentId() == null).toList();
        } else {
            roots = List.of(all.stream().filter(r -> r.getSlug().equals(rootSlug)).findFirst()
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND)));
        }
        List<Node> result = new ArrayList<>();
        for (Region root : sorted(roots)) {
            result.add(node(root, byParent, withAmbassadors));
        }
        return result;
    }

    private Node node(Region region, Map<UUID, List<Region>> byParent, Set<UUID> withAmbassadors) {
        List<Node> children = sorted(byParent.getOrDefault(region.getId(), List.of())).stream()
                .map(child -> node(child, byParent, withAmbassadors)).toList();
        boolean active = region.getType().isAccessible() && withAmbassadors.contains(region.getId());
        return new Node(region, active, children);
    }

    private static List<Region> sorted(List<Region> regions) {
        return regions.stream().sorted(java.util.Comparator.comparingInt(Region::getSortOrder)
                .thenComparing(Region::getName)).toList();
    }
}
