package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

/** AD1 admins list: ORGA only, grouping per user, unit kind / active / name-email filters, order and paging. */
class AdminListServiceTest {

    final RegionAdminRepository admins = mock(RegionAdminRepository.class);
    final RegionRepository regions = mock(RegionRepository.class);
    final UserService users = mock(UserService.class);
    final PermissionService permissions = mock(PermissionService.class);
    final AdminListService service = new AdminListService(admins, regions, users, permissions,
            mock(CurrentActorService.class));

    final Region zurich = region("Zürich", UnitType.CITY);
    final Region bern = region("Bern", UnitType.CITY);
    final Region welcome = region("Begrüssung Zürich", UnitType.WORKING_GROUP);
    final User anna = user("Anna Ambassador", "anna@local", true);
    final User bruno = user("bruno Botschafter", "bruno@local", false);
    final User carla = user("Carla Gruppe", "carla@example.org", true);

    @BeforeEach
    void setUp() {
        when(admins.findAll()).thenReturn(List.of(admin(bruno, bern), admin(anna, zurich), admin(anna, welcome),
                admin(carla, welcome), admin(anna, bern)));
        when(regions.findAllById(any())).thenReturn(List.of(zurich, bern, welcome));
        when(users.findAll(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return List.of(carla, anna, bruno).stream().filter(u -> ids.contains(u.getId())).toList();
        });
    }

    @Test
    void listsEveryAdminOnceWithTheirUnitsSortedByName() {
        Page<AdminListService.AdminEntry> page = service.list(null, null, null, 0, 20);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(e -> e.user().getDisplayName())
                .containsExactly("Anna Ambassador", "bruno Botschafter", "Carla Gruppe");
        assertThat(page.getContent().getFirst().units()).extracting(Region::getName)
                .containsExactly("Begrüssung Zürich", "Bern", "Zürich");
    }

    @Test
    void filtersByUnitKindActiveAndNameOrEmail() {
        assertThat(service.list(null, AdminListService.UnitKind.WORKING_GROUP, null, 0, 20).getContent())
                .extracting(e -> e.user().getDisplayName()).containsExactly("Anna Ambassador", "Carla Gruppe");
        assertThat(service.list(null, AdminListService.UnitKind.WORKING_GROUP, null, 0, 20).getContent().getFirst()
                .units()).containsExactly(welcome);
        assertThat(service.list(null, AdminListService.UnitKind.REGION, null, 0, 20).getContent())
                .extracting(e -> e.user().getDisplayName()).containsExactly("Anna Ambassador", "bruno Botschafter");
        assertThat(service.list(null, null, false, 0, 20).getContent()).extracting(e -> e.user().getDisplayName())
                .containsExactly("bruno Botschafter");
        assertThat(service.list("  EXAMPLE.org ", null, null, 0, 20).getContent())
                .extracting(e -> e.user().getDisplayName()).containsExactly("Carla Gruppe");
        assertThat(service.list("botsch", null, true, 0, 20).getContent()).isEmpty();
    }

    @Test
    void pagesThroughTheSortedList() {
        Page<AdminListService.AdminEntry> second = service.list(null, null, null, 1, 2);
        assertThat(second.getContent()).extracting(e -> e.user().getDisplayName()).containsExactly("Carla Gruppe");
        assertThat(second.getTotalPages()).isEqualTo(2);
        assertThat(service.list(null, null, null, 5, 2).getContent()).isEmpty();
    }

    @Test
    void validatesPagingAndNeedsOrga() {
        assertThatThrownBy(() -> service.list(null, null, null, -1, 20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(null, null, null, 0, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(null, null, null, 0, 101)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list("x".repeat(101), null, null, 0, 20)).isInstanceOf(ApiException.class);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions).requireOrga(any());
        assertThatThrownBy(() -> service.list(null, null, null, 0, 20)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", ApiErrorCode.ACCESS_DENIED);
    }

    private static Region region(String name, UnitType type) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setName(name);
        region.setType(type);
        return region;
    }

    private static User user(String name, String email, boolean active) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        user.setEmail(email);
        user.setActive(active);
        return user;
    }

    private static RegionAdmin admin(User user, Region region) {
        RegionAdmin admin = new RegionAdmin();
        admin.setUserId(user.getId());
        admin.setRegionId(region.getId());
        return admin;
    }
}
