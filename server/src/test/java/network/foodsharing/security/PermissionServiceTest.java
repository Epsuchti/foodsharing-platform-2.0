package network.foodsharing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * {@link PermissionService} with a stub {@link ScopedPermissionResolver}: ORGA overrides every scoped check, the
 * principal's user id is passed to the resolver, and without a resolver nobody holds scoped roles.
 */
class PermissionServiceTest {

    private static final UUID REGION = UUID.randomUUID();
    private static final UUID OTHER_REGION = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final SessionPrincipal orga = principal(UUID.randomUUID(), Role.ORGA);
    private final SessionPrincipal member = principal(MEMBER_ID, Role.FOODSAVER);
    private final SessionPrincipal stranger = principal(UUID.randomUUID(), Role.AMBASSADOR);

    /** member: member + admin of REGION, WELCOME function there, CAN_GIVE_INTRO_PICKUPS there, administrates USER. */
    private final ScopedPermissionResolver stub = new NoopScopedPermissionResolver() {
        @Override
        public boolean isMember(UUID userId, UUID regionOrGroupId) {
            return userId.equals(MEMBER_ID) && regionOrGroupId.equals(REGION);
        }

        @Override
        public boolean isAdmin(UUID userId, UUID regionOrGroupId) {
            return isMember(userId, regionOrGroupId);
        }

        @Override
        public boolean isAdminOfAny(UUID userId) {
            return userId.equals(MEMBER_ID);
        }

        @Override
        public boolean hasFunctionGroupPermission(UUID userId, WorkingGroupFunction function, UUID regionId) {
            return isMember(userId, regionId) && function == WorkingGroupFunction.WELCOME;
        }

        @Override
        public boolean hasRegionPermission(UUID userId, Permission permission, UUID regionId) {
            return isMember(userId, regionId) && permission == Permission.CAN_GIVE_INTRO_PICKUPS;
        }

        @Override
        public Map<UUID, Set<Permission>> permissionsByRegion(UUID userId) {
            return userId.equals(MEMBER_ID) ? Map.of(REGION, Set.of(Permission.CAN_GIVE_INTRO_PICKUPS)) : Map.of();
        }

        @Override
        public boolean mayAdministrateRegion(UUID actorId, UUID regionId) {
            return isMember(actorId, regionId);
        }

        @Override
        public boolean mayAdministrateUser(UUID actorId, UUID userId) {
            return actorId.equals(MEMBER_ID) && userId.equals(USER_ID);
        }
    };

    private final PermissionService service = withResolver(stub);

    @Test
    void orgaPassesEveryScopedCheckButIsNoAmbassador() {
        assertThat(service.has(orga, Permission.CAN_MANAGE_SURVEYS, OTHER_REGION)).isTrue();
        assertThat(service.hasAnywhere(orga, Permission.CAN_MANAGE_USERS)).isTrue();
        assertThat(service.hasFunction(orga, WorkingGroupFunction.REPORT, OTHER_REGION)).isTrue();
        assertThat(service.mayAdministrateRegion(orga, OTHER_REGION)).isTrue();
        assertThat(service.mayAdministrateUser(orga, UUID.randomUUID())).isTrue();
        assertThat(service.isAmbassadorOrOrga(orga, OTHER_REGION)).isTrue();
        assertThat(service.isAmbassador(orga, OTHER_REGION)).isFalse();
        assertThat(service.isMemberOf(orga, OTHER_REGION)).isFalse();
        assertThat(service.effectivePermissions(orga)).contains("CAN_GIVE_INTRO_PICKUPS@*", "CAN_MANAGE_SURVEYS@*");
    }

    @Test
    void scopedChecksAskTheResolverWithTheUserId() {
        assertThat(service.isMemberOf(member, REGION)).isTrue();
        assertThat(service.isAmbassador(member, REGION)).isTrue();
        assertThat(service.isAmbassadorOfAny(member)).isTrue();
        assertThat(service.hasFunction(member, WorkingGroupFunction.WELCOME, REGION)).isTrue();
        assertThat(service.hasFunction(member, WorkingGroupFunction.PR, REGION)).isFalse();
        assertThat(service.has(member, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).isTrue();
        assertThat(service.has(member, Permission.CAN_GIVE_INTRO_PICKUPS, OTHER_REGION)).isFalse();
        assertThat(service.has(member, Permission.CAN_GIVE_INTRO_PICKUPS, null)).isFalse();
        assertThat(service.hasAnywhere(member, Permission.CAN_GIVE_INTRO_PICKUPS)).isTrue();
        assertThat(service.hasAnywhere(member, Permission.CAN_MANAGE_USERS)).isFalse();
        assertThat(service.mayAdministrateUser(member, USER_ID)).isTrue();
        assertThat(service.effectivePermissions(member)).containsExactly("CAN_GIVE_INTRO_PICKUPS@" + REGION);

        assertThat(service.isMemberOf(stranger, REGION)).isFalse();
        assertThat(service.mayAdministrateUser(stranger, USER_ID)).isFalse();
        assertThat(service.effectivePermissions(stranger)).isEmpty();
    }

    @Test
    void noPrincipalHasNothing() {
        assertThat(service.has(null, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).isFalse();
        assertThat(service.isMemberOf(null, REGION)).isFalse();
        assertThat(service.mayAdministrateRegion(null, REGION)).isFalse();
        assertThat(service.effectivePermissions(null)).isEmpty();
    }

    @Test
    void withoutResolverNobodyHasScopedRoles() {
        for (PermissionService plain : new PermissionService[] {new PermissionService(), withResolver(null)}) {
            assertThat(plain.isMemberOf(member, REGION)).isFalse();
            assertThat(plain.has(member, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).isFalse();
            assertThat(plain.has(orga, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).isTrue();
            assertThat(plain.effectivePermissions(member)).isEmpty();
        }
    }

    @Test
    void roleHierarchyIsUnchanged() {
        assertThat(PermissionService.isAtLeast(Role.AMBASSADOR, Role.FOODSAVER)).isTrue();
        assertThat(PermissionService.isAtLeast(Role.FOODSHARER, Role.FOODSAVER)).isFalse();
        assertThat(service.hasRole(stranger, Role.STORE_MANAGER)).isTrue();
    }

    private static PermissionService withResolver(ScopedPermissionResolver resolver) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (resolver != null) {
            factory.registerSingleton("resolver", resolver);
        }
        return new PermissionService(factory.getBeanProvider(ScopedPermissionResolver.class));
    }

    private static SessionPrincipal principal(UUID id, Role role) {
        return new SessionPrincipal(id, UUID.randomUUID(), role, id + "@test.local");
    }
}
