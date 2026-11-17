package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Public region pages (plan §16, T11): working groups and inactive regions are not public (404), the contact address
 * is the region's email name at the platform domain, and the public tree marks a region active only when people can
 * join it and it has ambassadors.
 */
class PublicRegionServiceTest {

    private final RegionRepository regions = mock(RegionRepository.class);
    private final RegionAdminRepository admins = mock(RegionAdminRepository.class);
    private final RegionReadService reads = mock(RegionReadService.class);
    private final RegionOptionsService options = mock(RegionOptionsService.class);
    private final AppProperties properties = new AppProperties();
    private PublicRegionService service;

    private final Region canton = region("kanton-zuerich", UnitType.FEDERAL_STATE, null);
    private final Region zurich = region("zuerich-stadt", UnitType.CITY, canton.getId());
    private final Region winterthur = region("winterthur", UnitType.CITY, canton.getId());
    private final Region welcomeTeam = region("begruessungsteam-zuerich", UnitType.WORKING_GROUP, zurich.getId());

    private static Region region(String slug, UnitType type, UUID parent) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setSlug(slug);
        region.setName(slug);
        region.setType(type);
        region.setParentId(parent);
        return region;
    }

    @BeforeEach
    void setUp() {
        properties.setDomain("local.test");
        service = new PublicRegionService(regions, mock(RegionMembershipRepository.class), admins, reads, options,
                List.of(), properties);
        for (Region region : List.of(canton, zurich, winterthur, welcomeTeam)) {
            when(regions.findBySlug(region.getSlug())).thenReturn(Optional.of(region));
            when(regions.findById(region.getId())).thenReturn(Optional.of(region));
        }
        when(regions.findAll()).thenReturn(List.of(canton, zurich, winterthur, welcomeTeam));
        RegionAdmin bot = new RegionAdmin();
        bot.setRegionId(zurich.getId());
        when(admins.findAll()).thenReturn(List.of(bot));
        when(options.activePin(zurich.getId())).thenReturn(Optional.empty());
    }

    @Test
    void aRegionPageShowsAmbassadorsAndTheContactAddress() {
        zurich.setEmailName("zuerich");
        User reto = new User();
        reto.setDisplayName("Reto Baumann");
        when(reads.ambassadorsOf(zurich.getId())).thenReturn(List.of(reto));
        PublicRegionService.PublicRegionView view = service.region("zuerich-stadt");
        assertThat(view.contactEmail()).isEqualTo("zuerich@local.test");
        assertThat(view.ambassadorNames()).containsExactly("Reto Baumann");
        assertThat(view.parent()).isEqualTo(canton);
    }

    @Test
    void workingGroupsAndInactiveRegionsAreNotPublic() {
        assertThatThrownBy(() -> service.region("begruessungsteam-zuerich")).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_FOUND));
        winterthur.setActive(false);
        assertThatThrownBy(() -> service.region("winterthur")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.tree("nowhere")).isInstanceOf(ApiException.class);
    }

    @Test
    void theTreeMarksJoinableRegionsWithAmbassadorsActiveAndLeavesGroupsOut() {
        List<PublicRegionService.Node> tree = service.tree("kanton-zuerich");
        assertThat(tree).singleElement().satisfies(root -> {
            assertThat(root.active()).isFalse(); // a federal state is not joinable
            assertThat(root.children()).extracting(child -> child.region().getSlug())
                    .containsExactlyInAnyOrder("zuerich-stadt", "winterthur");
            assertThat(root.children()).filteredOn(child -> child.region() == zurich).singleElement()
                    .satisfies(child -> assertThat(child.active()).isTrue());
            assertThat(root.children()).filteredOn(child -> child.region() == winterthur).singleElement()
                    .satisfies(child -> assertThat(child.active()).isFalse());
        });
    }
}
