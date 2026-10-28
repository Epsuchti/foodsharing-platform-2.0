package network.foodsharing.fsp;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.service.FspRules;
import network.foodsharing.fsp.service.FspRules.Facts;
import org.junit.jupiter.api.Test;

/** FS1 permission matrix (upstream FoodSharePointPermissions). */
class FspPermissionMatrixTest {

    private static Facts facts(boolean mayAdd, boolean member, boolean manager, boolean follower, boolean creator,
                               FspStatus status) {
        return new Facts(mayAdd, member, manager, follower, creator, status);
    }

    @Test
    void creationStatusDependsOnTheActor() {
        assertThat(FspRules.createStatus(true, false)).isEqualTo(FspStatus.ACTIVE);
        assertThat(FspRules.createStatus(true, true)).isEqualTo(FspStatus.ACTIVE);
        assertThat(FspRules.createStatus(false, true)).isEqualTo(FspStatus.INACTIVE);
        assertThat(FspRules.createStatus(false, false)).isNull();
    }

    @Test
    void activatorsMayEverything() {
        Facts activator = facts(true, false, false, false, false, FspStatus.INACTIVE);
        assertThat(FspRules.mayView(activator)).isTrue();
        assertThat(FspRules.mayEdit(activator)).isTrue();
        assertThat(FspRules.mayDelete(activator)).isTrue();
        assertThat(FspRules.mayActivate(activator)).isTrue();
        assertThat(FspRules.mayPost(activator)).isTrue();
        assertThat(FspRules.mayDeletePost(activator, false)).isTrue();
    }

    @Test
    void managersEditButNeitherDeleteNorActivate() {
        Facts manager = facts(false, true, true, true, false, FspStatus.ACTIVE);
        assertThat(FspRules.mayEdit(manager)).isTrue();
        assertThat(FspRules.mayDelete(manager)).isFalse();
        assertThat(FspRules.mayActivate(manager)).isFalse();
        assertThat(FspRules.mayDeletePost(manager, false)).isFalse();
        assertThat(FspRules.mayDeletePost(manager, true)).isTrue();
    }

    @Test
    void inactiveFspsAreHiddenFromOthersButVisibleToTheCreator() {
        assertThat(FspRules.mayView(facts(false, true, false, false, false, FspStatus.INACTIVE))).isFalse();
        Facts creator = facts(false, true, false, false, true, FspStatus.INACTIVE);
        assertThat(FspRules.mayView(creator)).isTrue();
        assertThat(FspRules.mayEdit(creator)).isFalse();
        assertThat(FspRules.mayPost(creator)).isFalse();
        assertThat(FspRules.mayFollow(creator)).isFalse();
    }

    @Test
    void wallIsForRegionMembersAndFollowers() {
        assertThat(FspRules.mayPost(facts(false, true, false, false, false, FspStatus.ACTIVE))).isTrue();
        assertThat(FspRules.mayPost(facts(false, false, false, true, false, FspStatus.ACTIVE))).isTrue();
        Facts stranger = facts(false, false, false, false, false, FspStatus.ACTIVE);
        assertThat(FspRules.mayPost(stranger)).isFalse();
        assertThat(FspRules.mayView(stranger)).isTrue();
        assertThat(FspRules.mayFollow(stranger)).isTrue();
        assertThat(FspRules.mayEdit(stranger)).isFalse();
    }
}
