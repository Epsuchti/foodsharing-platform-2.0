package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PU3 slot dialog (decision 164): details of one sign-up for managers and co-pickups ({@link SlotDetailRule}). */
@Service
@Transactional(readOnly = true)
public class PickupSignupDetailService {

    /** One sign-up with the person's numbers at this store; {@code phone} only under S9. */
    public record SignupDetail(User user, boolean confirmed, Instant signedUpAt, String phone, int pickupCount,
                               Instant lastPickupAt, List<Instant> upcomingSlots) {
    }

    private final PickupSignupRepository signups;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final Clock clock;

    public PickupSignupDetailService(PickupSignupRepository signups, StoreService stores,
                                     StorePermissionService rights, UserService users,
                                     CurrentActorService currentActor, Clock clock) {
        this.signups = signups;
        this.stores = stores;
        this.rights = rights;
        this.users = users;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /** @throws ApiException 404 STORE_NOT_FOUND / STORE_DELETED / SIGNUP_NOT_FOUND, 403 ACCESS_DENIED */
    public SignupDetail detail(UUID storeId, Instant pickupAt, UUID userId) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.Rights r = rights.rights(actor, store);
        boolean coPickup = signups.findByStoreIdAndUserIdAndPickupAt(storeId, actor.userId(), pickupAt).isPresent();
        StorePermissionService.require(SlotDetailRule.mayView(r.mayConfirmPickups(), r.mayEditPickups(), coPickup));
        PickupSignup signup = signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND));
        User user = users.findAll(List.of(userId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND));
        Instant now = clock.instant();
        // same numbers as the team list (StoreTeamService via StorePickupInfo.memberStats)
        var stats = signups.memberStats(storeId, now).stream().filter(s -> s.getUserId().equals(userId))
                .findFirst();
        List<Instant> upcoming = signups.findFutureOfUser(storeId, userId, now).stream()
                .map(PickupSignup::getPickupAt).sorted(Comparator.naturalOrder()).toList();
        return new SignupDetail(user, signup.isConfirmed(), signup.getCreatedAt(),
                r.maySeePhoneNumbers() ? user.getMobilePhone() : null,
                stats.map(s -> (int) s.getPickupCount()).orElse(0),
                stats.map(s -> s.getLastPickupAt()).orElse(null), upcoming);
    }
}
