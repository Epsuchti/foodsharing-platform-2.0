package network.foodsharing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.resource.domain.Resource;
import network.foodsharing.resource.domain.ResourceLinks;
import network.foodsharing.resource.domain.ResourceOpenness;
import network.foodsharing.resource.repository.ResourceBuddyLookup;
import network.foodsharing.resource.repository.ResourceCategoryLinkRepository;
import network.foodsharing.resource.repository.ResourceCategoryNameRepository;
import network.foodsharing.resource.repository.ResourceCategoryRepository;
import network.foodsharing.resource.repository.ResourceFavouriteRepository;
import network.foodsharing.resource.repository.ResourceImageRepository;
import network.foodsharing.resource.repository.ResourceRepository;
import network.foodsharing.resource.service.ResourceService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * C6 service paths without a database (formerly ResourceFlowIT): a region's list leaves out the private resources
 * of people who are not one's buddies, a hidden private resource cannot be favoured (404, so its existence stays
 * secret), favouring is idempotent, commons need the region's RESOURCES function and own resources the per-user limit.
 */
class ResourceServiceFlowTest {

    static final UUID REGION = UUID.randomUUID();
    static final SessionPrincipal ME = TestAuth.principal(SeedUsers.FOODSAVER);
    static final UUID STRANGER = UUID.randomUUID();

    ResourceRepository resources = mock(ResourceRepository.class);
    ResourceFavouriteRepository favourites = mock(ResourceFavouriteRepository.class);
    ResourceBuddyLookup buddies = mock(ResourceBuddyLookup.class);
    RegionService regions = mock(RegionService.class);
    PermissionService permissions = mock(PermissionService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class);
    ResourceService service = new ResourceService(resources, mock(ResourceCategoryRepository.class),
            mock(ResourceCategoryNameRepository.class), mock(ResourceCategoryLinkRepository.class),
            mock(ResourceImageRepository.class), favourites, buddies, regions, mock(UserService.class),
            mock(UploadService.class), mock(NotificationService.class), mock(FeatureToggleService.class),
            currentActor, permissions, history, new AppProperties(),
            Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(currentActor.requirePrincipal()).thenReturn(ME);
        Region city = new Region();
        city.setId(REGION);
        city.setType(UnitType.CITY);
        when(regions.require(REGION)).thenReturn(city);
        when(permissions.isMemberOf(ME, REGION)).thenReturn(true);
        when(buddies.confirmedBuddies(ME.userId())).thenReturn(Set.of());
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
    }

    static Resource owned(UUID owner, boolean privateResource) {
        Resource resource = new Resource();
        resource.setId(UUID.randomUUID());
        resource.setOwnerId(owner);
        resource.setRegionId(REGION);
        resource.setName("Akkubohrmaschine");
        resource.setPrivateResource(privateResource);
        return resource;
    }

    private static ApiErrorCode code(Throwable error) {
        return ((ApiException) error).getCode();
    }

    @Test
    void theRegionListLeavesOutStrangersPrivateResources() {
        Resource open = owned(STRANGER, false);
        Resource hidden = owned(STRANGER, true);
        when(regions.activeMemberIds(REGION)).thenReturn(List.of(STRANGER, ME.userId()));
        when(resources.findOwnedIn(any(), any())).thenReturn(List.of(open, hidden));

        assertThat(service.regionResources(REGION)).extracting(view -> view.resource().getId())
                .containsExactly(open.getId());
    }

    @Test
    void aHiddenPrivateResourceCannotBeFavouredAndFavouringIsIdempotent() {
        Resource hidden = owned(STRANGER, true);
        when(resources.findByIdAndDeletedAtIsNull(hidden.getId())).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> service.favourite(hidden.getId()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));

        Resource open = owned(STRANGER, false);
        when(resources.findByIdAndDeletedAtIsNull(open.getId())).thenReturn(Optional.of(open));
        when(favourites.findByUserIdAndResourceId(ME.userId(), open.getId())).thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ResourceLinks.Favourite(ME.userId(), open.getId())));
        service.favourite(open.getId());
        service.favourite(open.getId());
        verify(favourites, times(1)).save(any());
    }

    @Test
    void commonsNeedTheResourcesFunctionAndOwnResourcesTheLimit() {
        ResourceService.Input commons = new ResourceService.Input("Pavillon", null, List.of(UUID.randomUUID()), false,
                ResourceOpenness.ASK_FIRST, REGION, null, true);
        when(permissions.hasFunction(ME, WorkingGroupFunction.RESOURCES, REGION)).thenReturn(false);
        assertThatThrownBy(() -> service.create(commons))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.ACCESS_DENIED));

        ResourceService.Input own = new ResourceService.Input("Leiter", null, List.of(UUID.randomUUID()), false,
                ResourceOpenness.ASK_FIRST, null, null, false);
        when(resources.countByOwnerIdAndDeletedAtIsNull(ME.userId())).thenReturn(10L);
        assertThatThrownBy(() -> service.create(own))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.RESOURCE_LIMIT_REACHED));
        verify(resources, never()).save(any());
    }
}
