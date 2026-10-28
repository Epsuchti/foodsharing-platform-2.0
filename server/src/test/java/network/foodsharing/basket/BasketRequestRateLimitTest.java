package network.foodsharing.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketKind;
import network.foodsharing.basket.domain.BasketLocationType;
import network.foodsharing.basket.service.BasketQueryService;
import network.foodsharing.basket.service.BasketService;
import network.foodsharing.basket.service.BasketService.BasketInput;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Basket requests per user and hour are limited (429 BASKET_REQUEST_RATE_LIMITED). */
@JpaSliceTest
@Import({BasketService.class, BasketQueryService.class})
@TestPropertySource(properties = "app.baskets.max-requests-per-hour=2")
class BasketRequestRateLimitTest {

    @Autowired
    private BasketService service;

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

    @MockitoBean
    private network.foodsharing.admin.service.FeatureToggleService toggles;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void theThirdRequestWithinAnHourIsRejected() {
        when(groceries.groceryNames(anyCollection())).thenReturn(Map.of());
        User owner = users.saveAndFlush(TestUsers.user("rl-owner@test.local", Role.FOODSAVER));
        User requester = users.saveAndFlush(TestUsers.user("rl-requester@test.local", Role.FOODSAVER));
        TestAuth.login(TestUsers.principal(owner));
        List<Basket> created = List.of(create(), create(), create(), create());

        TestAuth.login(TestUsers.principal(requester));
        service.request(created.get(0).getId(), null);
        service.request(created.get(1).getId(), null);
        assertThatThrownBy(() -> service.request(created.get(2).getId(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.BASKET_REQUEST_RATE_LIMITED);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });

        clock.fixAt(clock.instant().plus(Duration.ofMinutes(61)));
        assertThat(service.request(created.get(3).getId(), null)).isNotNull();
    }

    private Basket create() {
        return service.create(new BasketInput("Brot", BasketLocationType.CUSTOM, true, false, false, null, 500, null,
                List.of(BasketKind.BAKED_GOODS), 47.5, 8.72, null, null, null));
    }
}
