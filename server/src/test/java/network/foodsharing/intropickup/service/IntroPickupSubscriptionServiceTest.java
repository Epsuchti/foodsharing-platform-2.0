package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickupSubscription;
import network.foodsharing.intropickup.domain.SubscriptionChannel;
import network.foodsharing.intropickup.repository.IntroPickupSubscriptionRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * EA9: only unverified members of the region subscribe to new EinAb dates (e-mail to the account address by default);
 * unsubscribing by link needs a known token, and the token changes when a subscription becomes active again.
 */
class IntroPickupSubscriptionServiceTest {

    private static final UUID REGION = UUID.randomUUID();

    private final IntroPickupSubscriptionRepository subscriptions = mock(IntroPickupSubscriptionRepository.class);
    private final RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final IntroPickupSubscriptionService service = new IntroPickupSubscriptionService(subscriptions,
            mock(RegionService.class), resolver, currentActor, mock(HistoryService.class, RETURNS_DEEP_STUBS), events);
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID());
        user.setEmail("luca@example.org");
        user.setPreferredLanguage(Language.DE);
        when(currentActor.requireUser()).thenReturn(user);
        when(currentActor.requireUserId()).thenReturn(user.getId());
        when(resolver.isMember(user.getId(), REGION)).thenReturn(true);
        when(subscriptions.findByUserId(user.getId())).thenReturn(Optional.empty());
    }

    @Test
    void verifiedUsersNeedNoNotifications() {
        user.setVerified(true);
        assertThatThrownBy(() -> service.subscribe(REGION, SubscriptionChannel.IN_APP, null, Language.DE))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOTIFICATION_NOT_NEEDED_VERIFIED));
        verify(subscriptions, never()).save(any());
    }

    @Test
    void onlyMembersOfTheRegionSubscribe() {
        when(resolver.isMember(user.getId(), REGION)).thenReturn(false);
        assertThatThrownBy(() -> service.subscribe(REGION, SubscriptionChannel.IN_APP, null, Language.DE))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_REGION_MEMBER));
    }

    @Test
    void theEmailChannelDefaultsToTheAccountAddressAndRejectsABrokenOne() {
        IntroPickupSubscription subscription = service.subscribe(REGION, SubscriptionChannel.EMAIL, " ", null);
        assertThat(subscription.isActive()).isTrue();
        assertThat(subscription.getDeliveryEmail()).isEqualTo("luca@example.org");
        assertThat(subscription.getLanguage()).isEqualTo(Language.DE.getValue());
        assertThat(subscription.getUnsubscribeToken()).isNotBlank();

        assertThatThrownBy(() -> service.subscribe(REGION, SubscriptionChannel.EMAIL, "no-address", null))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    @Test
    void reactivatingRotatesTheTokenAndUnknownTokensAreRejected() {
        IntroPickupSubscription existing = new IntroPickupSubscription();
        existing.setUserId(user.getId());
        existing.setActive(false);
        existing.setUnsubscribeToken("old-token");
        when(subscriptions.findByUserId(user.getId())).thenReturn(Optional.of(existing));
        service.subscribe(REGION, SubscriptionChannel.IN_APP, null, Language.DE);
        assertThat(existing.getUnsubscribeToken()).isNotEqualTo("old-token");

        when(subscriptions.findByUnsubscribeToken(existing.getUnsubscribeToken())).thenReturn(Optional.of(existing));
        service.unsubscribeByToken(" " + existing.getUnsubscribeToken() + " ");
        assertThat(existing.isActive()).isFalse();

        assertThatThrownBy(() -> service.unsubscribeByToken("nope"))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiErrorCode.INVALID_UNSUBSCRIBE_TOKEN));
        assertThatThrownBy(() -> service.unsubscribeByToken(null))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiErrorCode.INVALID_UNSUBSCRIBE_TOKEN));
    }
}
