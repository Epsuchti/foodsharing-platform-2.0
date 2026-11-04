package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.security.WorkingGroupFunction;
import org.junit.jupiter.api.Test;

/** Decision 162: "administrates a region" (newsletters menu item) excludes admins of plain working groups. */
class RegionAnyAdministrationTest {

    private static final UUID USER = UUID.randomUUID();

    private final RegionAdminRepository admins = mock(RegionAdminRepository.class);
    private final RegionFunctionRepository functions = mock(RegionFunctionRepository.class);
    private final RegionRepository regions = mock(RegionRepository.class);
    private final RegionPermissionResolver resolver = new RegionPermissionResolver(
            mock(RegionMembershipRepository.class), admins, functions, mock(RegionPermissionRepository.class), regions,
            mock(RegionClosureRepository.class), new AppProperties());

    private static Region unit(UnitType type) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setType(type);
        return region;
    }

    private void adminOf(Region unit, WorkingGroupFunction function) {
        when(admins.findRegionIds(USER)).thenReturn(Set.of(unit.getId()));
        when(regions.findByIdIn(any())).thenReturn(List.of(unit));
        if (function == null) {
            when(functions.findByGroupIdIn(any())).thenReturn(List.of());
        } else {
            RegionFunction assignment = new RegionFunction();
            assignment.setGroupId(unit.getId());
            assignment.setRegionId(UUID.randomUUID());
            assignment.setFunction(function);
            when(functions.findByGroupIdIn(any())).thenReturn(List.of(assignment));
        }
    }

    @Test
    void ambassadorOfARegionAdministratesOne() {
        adminOf(unit(UnitType.CITY), null);
        assertThat(resolver.administratesAnyRegion(USER)).isTrue();
    }

    @Test
    void fsManagementAdminAdministratesTheRegionOfTheGroup() {
        adminOf(unit(UnitType.WORKING_GROUP), WorkingGroupFunction.FSMANAGEMENT);
        assertThat(resolver.administratesAnyRegion(USER)).isTrue();
    }

    @Test
    void adminOfAnotherWorkingGroupDoesNot() {
        adminOf(unit(UnitType.WORKING_GROUP), WorkingGroupFunction.STORES_COORDINATION);
        assertThat(resolver.administratesAnyRegion(USER)).isFalse();
    }

    @Test
    void nobodyWithoutAdminRows() {
        when(admins.findRegionIds(USER)).thenReturn(Set.of());
        assertThat(resolver.administratesAnyRegion(USER)).isFalse();
    }
}
