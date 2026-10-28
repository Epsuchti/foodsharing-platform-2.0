package network.foodsharing.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.basket.service.BasketQueryService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.service.EventService;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.service.FspQueryService;
import network.foodsharing.map.service.BubbleService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** M1 bubbles: anonymous only FSP/basket/region, kind parsing, contents and the user-bubble permission. */
class BubbleServiceTest {

    private final StoreQueryService stores = mock(StoreQueryService.class);
    private final FspQueryService fsps = mock(FspQueryService.class);
    private final BasketQueryService baskets = mock(BasketQueryService.class);
    private final EventService events = mock(EventService.class);
    private final RegionService regions = mock(RegionService.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), null, Role.FOODSAVER, "a@local");
    private BubbleService service;

    @BeforeEach
    void setUp() {
        service = new BubbleService(stores, fsps, baskets, events, regions, users, actors, permissions);
        when(actors.requirePrincipal()).thenReturn(actor);
    }

    private static ApiErrorCode code(Throwable t) {
        return ((ApiException) t).getCode();
    }

    @Test
    void anonymousCallersGetOnlyPublicKinds() {
        for (String kind : new String[] {"store", "event", "user"}) {
            assertThatThrownBy(() -> service.bubble(kind, UUID.randomUUID(), true))
                    .satisfies(t -> assertThat(code(t)).isEqualTo(ApiErrorCode.MARKER_NOT_FOUND));
        }
        verifyNoInteractions(stores, events, users, permissions);
    }

    @Test
    void unknownKindIsAValidationError() {
        assertThatThrownBy(() -> service.bubble("planet", UUID.randomUUID(), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.bubble(null, UUID.randomUUID(), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    @Test
    void fspBubbleHasPhotoAddressAndFollowers() {
        UUID id = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        FoodSharePoint fsp = new FoodSharePoint();
        fsp.setName("Fairteiler");
        fsp.setCity("Zürich");
        fsp.setPostcode("8003");
        fsp.setStreet("Weg 1");
        fsp.setPhotoUploadId(photo);
        when(fsps.findActive(id)).thenReturn(Optional.of(fsp));
        when(fsps.followerCount(id)).thenReturn(7);
        var bubble = service.bubble("FSP", id, true);
        assertThat(bubble.title()).isEqualTo("Fairteiler");
        assertThat(bubble.imageUrl()).isEqualTo("/api/uploads/" + photo + "?size=md");
        assertThat(bubble.link()).isEqualTo("/food-share-points/" + id);
        assertThat(bubble.lines()).containsExactly("Weg 1", "8003 Zürich", "7");
    }

    @Test
    void inactiveOrUnknownFspIsNotFound() {
        when(fsps.findActive(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.bubble("fsp", UUID.randomUUID(), true))
                .satisfies(t -> assertThat(code(t)).isEqualTo(ApiErrorCode.MARKER_NOT_FOUND));
    }

    @Test
    void regionBubbleShowsMemberCount() {
        UUID id = UUID.randomUUID();
        Region region = new Region();
        ReflectionTestUtils.setField(region, "id", id);
        region.setName("Zürich Stadt");
        region.setType(UnitType.BIG_CITY);
        when(regions.find(id)).thenReturn(Optional.of(region));
        when(regions.activeMemberCount(id)).thenReturn(12L);
        var bubble = service.bubble("region_pin", id, true);
        assertThat(bubble.title()).isEqualTo("Zürich Stadt");
        assertThat(bubble.subtitle()).isEqualTo("BIG_CITY");
        assertThat(bubble.link()).isEqualTo("/regions/" + id);
        assertThat(bubble.lines()).containsExactly("12");
    }

    @Test
    void userBubbleNeedsMayAdministrateUser() {
        UUID id = UUID.randomUUID();
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateUser(actor, id);
        assertThatThrownBy(() -> service.bubble("user", id, false))
                .satisfies(t -> assertThat(code(t)).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        verifyNoInteractions(users);

        UUID other = UUID.randomUUID();
        User user = new User();
        user.setDisplayName("Jumper");
        user.setRole(Role.FOODSAVER);
        when(users.findActive(other)).thenReturn(Optional.of(user));
        var bubble = service.bubble("user", other, false);
        assertThat(bubble.title()).isEqualTo("Jumper");
        assertThat(bubble.subtitle()).isEqualTo("FOODSAVER");
        assertThat(bubble.link()).isEqualTo("/profile/" + other);
    }
}
