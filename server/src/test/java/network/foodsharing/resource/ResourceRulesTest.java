package network.foodsharing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.resource.domain.Resource;
import network.foodsharing.resource.service.ResourceRules;
import org.junit.jupiter.api.Test;

/** Upstream ResourcePermissions (C6): the pure {@link ResourceRules}, incl. the per-user limit. */
class ResourceRulesTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static Resource owned(boolean privateResource) {
        Resource r = new Resource();
        r.setOwnerId(OWNER);
        r.setPrivateResource(privateResource);
        return r;
    }

    @Test
    void onlyCitiesPartsOfTownAndGroupsHaveResources() {
        assertThat(ResourceRules.maySee(UnitType.CITY, true)).isTrue();
        assertThat(ResourceRules.maySee(UnitType.BIG_CITY, true)).isTrue();
        assertThat(ResourceRules.maySee(UnitType.PART_OF_TOWN, true)).isTrue();
        assertThat(ResourceRules.maySee(UnitType.WORKING_GROUP, true)).isTrue();
        assertThat(ResourceRules.maySee(UnitType.CITY, false)).isFalse();
        assertThat(ResourceRules.maySee(UnitType.COUNTRY, true)).isFalse();
        assertThat(ResourceRules.maySee(UnitType.FEDERAL_STATE, true)).isFalse();
        assertThat(ResourceRules.hasResources(null)).isFalse();
    }

    @Test
    void resourcesAvailableIsTheSameRuleAsTheUnitType() {
        // RegionDetail.resourcesAvailable reads UnitType.hasResources; the endpoints read ResourceRules.hasResources
        for (UnitType type : UnitType.values()) {
            assertThat(ResourceRules.hasResources(type)).as(type.name()).isEqualTo(type.hasResources());
        }
        assertThat(java.util.Arrays.stream(UnitType.values()).filter(UnitType::hasResources))
                .containsExactlyInAnyOrder(UnitType.BIG_CITY, UnitType.CITY, UnitType.PART_OF_TOWN,
                        UnitType.WORKING_GROUP);
    }

    @Test
    void addLimit() {
        assertThat(ResourceRules.mayAdd(0, 10)).isTrue();
        assertThat(ResourceRules.mayAdd(9, 10)).isTrue();
        assertThat(ResourceRules.mayAdd(10, 10)).isFalse();
        assertThat(ResourceRules.mayAdd(11, 10)).isFalse();
        assertThat(ResourceRules.mayAdd(0, 0)).isFalse();
    }

    @Test
    void editAndDelete() {
        Resource own = owned(false);
        assertThat(ResourceRules.mayEdit(own, OWNER, false)).isTrue();
        assertThat(ResourceRules.mayEdit(own, OTHER, true)).isFalse();
        assertThat(ResourceRules.mayDelete(own, OTHER, false, false)).isFalse();
        assertThat(ResourceRules.mayDelete(own, OTHER, true, false)).isTrue();

        Resource commons = new Resource();
        assertThat(commons.isCommons()).isTrue();
        assertThat(ResourceRules.mayEdit(commons, OTHER, true)).isTrue();
        assertThat(ResourceRules.mayEdit(commons, OTHER, false)).isFalse();
        assertThat(ResourceRules.mayDelete(commons, OTHER, false, true)).isTrue();
        assertThat(ResourceRules.mayDelete(commons, OTHER, false, false)).isFalse();
    }

    @Test
    void privateResourcesOnlyForOwnerAndBuddies() {
        Resource priv = owned(true);
        assertThat(ResourceRules.visibleTo(priv, OWNER, Set.of())).isTrue();
        assertThat(ResourceRules.visibleTo(priv, OTHER, Set.of())).isFalse();
        assertThat(ResourceRules.visibleTo(priv, OTHER, Set.of(OWNER))).isTrue();
        assertThat(ResourceRules.visibleTo(owned(false), OTHER, Set.of())).isTrue();
    }
}
