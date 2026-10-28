package network.foodsharing.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketKind;
import network.foodsharing.basket.domain.BasketLocationType;
import network.foodsharing.basket.domain.BasketRequestStatus;
import network.foodsharing.basket.domain.BasketStatus;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.basket.service.BasketQueryService;
import network.foodsharing.basket.service.BasketRules;
import network.foodsharing.basket.service.BasketService;
import network.foodsharing.basket.service.BasketService.BasketInput;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.store.service.StoreCategoryService;
import network.foodsharing.store.service.TeamChatPort;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** B1/B2: basket validation, contact rules, requests, answers, nearby, expiry and permissions. */
@JpaSliceTest
@Import({BasketService.class, BasketQueryService.class})
class BasketServiceTest {

    @Autowired
    private BasketService service;

    @Autowired
    private BasketQueryService queries;

    @Autowired
    private BasketRepository baskets;

    @Autowired
    private UserRepository users;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private StoreCategoryService groceries;

    @MockitoBean
    private UploadService uploads;

    @MockitoBean
    private NotificationService notifications;

    @MockitoBean
    private TeamChatPort chat;

    private User owner;
    private User other;
    private User orga;

    @BeforeEach
    void setUp() {
        owner = TestUsers.user("owner@test.local", Role.FOODSAVER);
        owner.setLatitude(47.3891);
        owner.setLongitude(8.5487);
        owner = users.saveAndFlush(owner);
        other = users.saveAndFlush(TestUsers.user("other@test.local", Role.FOODSHARER));
        orga = users.saveAndFlush(TestUsers.user("orga@test.local", Role.ORGA));
        when(groceries.groceryNames(anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static BasketInput input(BasketLocationType type, Double lat, Double lon, boolean message, boolean phone,
                                     String phoneNumber) {
        return new BasketInput("Brot", type, message, phone, false, null, 500, null, List.of(BasketKind.BAKED_GOODS),
                lat, lon, phoneNumber, null, null);
    }

    private Basket create(User user) {
        TestAuth.login(TestUsers.principal(user));
        return service.create(input(BasketLocationType.HOME, null, null, true, false, null));
    }

    @Test
    void homeNeedsOwnerCoordinatesCustomNeedsLatLon() {
        TestAuth.login(TestUsers.principal(other));
        assertThatThrownBy(() -> service.create(input(BasketLocationType.HOME, null, null, true, false, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_LOCATION_REQUIRED));
        assertThatThrownBy(() -> service.create(input(BasketLocationType.CUSTOM, 47.0, null, true, false, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_LOCATION_REQUIRED));
        Basket custom = service.create(input(BasketLocationType.CUSTOM, 47.5, 8.72, true, false, null));
        assertThat(custom.getLat()).isEqualTo(47.5);
        assertThat(custom.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
    }

    @Test
    void contactRules() {
        TestAuth.login(TestUsers.principal(owner));
        assertThatThrownBy(() -> service.create(input(BasketLocationType.HOME, null, null, false, false, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_CONTACT_REQUIRED));
        assertThatThrownBy(() -> service.create(input(BasketLocationType.HOME, null, null, false, true, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.INVALID_PHONE_NUMBER));
        Basket withPhone = service.create(input(BasketLocationType.HOME, null, null, false, true, "044 123 45 67"));
        assertThat(withPhone.getPhone()).isEqualTo("+41441234567");
        assertThat(withPhone.getLat()).isEqualTo(47.3891);
    }

    @Test
    void requestNotifiesOwnerAndSendsTheMessageAsChat() {
        Basket basket = create(owner);
        assertThatThrownBy(() -> service.request(basket.getId(), "hi"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_OWN));

        TestAuth.login(TestUsers.principal(other));
        service.request(basket.getId(), " Kann ich es holen? ");
        assertThatThrownBy(() -> service.request(basket.getId(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_ALREADY_REQUESTED));
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().recipientIds()).containsExactly(owner.getId());
        assertThat(bell.getValue().identifier()).isEqualTo("BASKET_REQUEST-" + basket.getId() + "-" + other.getId());
        assertThat(bell.getValue().link()).isEqualTo("/baskets/" + basket.getId());
        verify(chat).sendDirect(other.getId(), owner.getId(), "Kann ich es holen?");
        assertThat(baskets.findById(basket.getId()).orElseThrow().getRequestCount()).isEqualTo(1);

        service.withdraw(basket.getId());
        service.request(basket.getId(), null);
        assertThat(baskets.findById(basket.getId()).orElseThrow().getRequestCount()).isEqualTo(1);
    }

    @Test
    void ownerAnswersAndPickedUpTakesTheBasket() {
        Basket basket = create(owner);
        TestAuth.login(TestUsers.principal(other));
        service.request(basket.getId(), null);
        assertThatThrownBy(() -> service.answer(basket.getId(), other.getId(), BasketRequestStatus.PICKED_UP))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));

        TestAuth.login(TestUsers.principal(owner));
        assertThat(queries.get(basket.getId()).requests()).hasSize(1);
        assertThatThrownBy(() -> service.answer(basket.getId(), other.getId(), BasketRequestStatus.WITHDRAWN))
                .isInstanceOf(ApiException.class);
        service.answer(basket.getId(), other.getId(), BasketRequestStatus.PICKED_UP);
        Basket taken = baskets.findById(basket.getId()).orElseThrow();
        assertThat(taken.getStatus()).isEqualTo(BasketStatus.TAKEN);
        assertThat(taken.getTakenAt()).isNotNull();
        verify(notifications).removeBells("BASKET_REQUEST-" + basket.getId() + "-" + other.getId());

        TestAuth.login(TestUsers.principal(other));
        assertThatThrownBy(() -> service.request(basket.getId(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_NOT_ACTIVE));
    }

    @Test
    void nearbyFiltersByDistanceAndRoundsHomeCoordinatesOfOthers() {
        Basket home = create(owner);
        TestAuth.login(TestUsers.principal(orga));
        service.create(input(BasketLocationType.CUSTOM, 47.4997, 8.7241, true, false, null));

        TestAuth.login(TestUsers.principal(other));
        var near = queries.nearby(47.3769, 8.5417, 5);
        assertThat(near).singleElement().satisfies(view -> {
            assertThat(view.basket().getId()).isEqualTo(home.getId());
            assertThat(view.lat()).isEqualTo(47.389);
            assertThat(view.lon()).isEqualTo(8.549);
            assertThat(view.distanceKm()).isBetween(1.0, 2.0);
        });
        assertThat(queries.nearby(47.3769, 8.5417, 50)).hasSize(2);

        TestAuth.login(TestUsers.principal(owner));
        assertThat(queries.mine()).singleElement().satisfies(view -> assertThat(view.lat()).isEqualTo(47.3891));
    }

    @Test
    void expiryAndDeletionPermissions() {
        Basket basket = create(owner);
        clock.fixAt(clock.instant().plus(Duration.ofDays(8)));
        assertThat(service.expire()).isEqualTo(1);
        assertThat(baskets.findById(basket.getId()).orElseThrow().getStatus()).isEqualTo(BasketStatus.EXPIRED);
        assertThat(service.expire()).isZero();

        Basket second = create(owner);
        TestAuth.login(TestUsers.principal(other));
        assertThatThrownBy(() -> service.delete(second.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        TestAuth.login(TestUsers.principal(orga));
        service.delete(second.getId());
        assertThat(baskets.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(BasketStatus.DELETED);
        assertThatThrownBy(() -> queries.get(second.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_NOT_FOUND));
        verify(chat, never()).sendDirect(any(), any(), any());
    }

    @Test
    void rules() {
        assertThat(BasketRules.distanceKm(47.3769, 8.5417, 47.4997, 8.7241)).isBetween(18.0, 20.0);
        assertThat(BasketRules.isLive(BasketRequestStatus.WITHDRAWN)).isFalse();
        Basket basket = new Basket();
        basket.setOwnerId(UUID.randomUUID());
        assertThat(BasketRules.mayDelete(basket, UUID.randomUUID(), true)).isTrue();
        assertThat(BasketRules.mayDelete(basket, UUID.randomUUID(), false)).isFalse();
        assertThat(BasketRules.mayDelete(basket, basket.getOwnerId(), false)).isTrue();
    }
}
