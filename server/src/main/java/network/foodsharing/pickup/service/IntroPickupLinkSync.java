package network.foodsharing.pickup.service;

import network.foodsharing.intropickup.service.IntroPickupGuideService;
import network.foodsharing.intropickup.service.IntroPickupGuideService.AutoCancelCause;
import network.foodsharing.pickup.domain.PickupEvents;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * EA16 cancel sync (decision 169): when a sign-up goes away, EinAbs linked to it are cancelled in the same transaction;
 * when the pickup moves (PU10, decision 170), they move along.
 * Every removal path counts: sign-out or removal by a manager, the automation, "delete all future pickups", leaving or
 * removal from the store team. It lives in the pickup module because pickup already reads intropickup (day agenda);
 * intropickup must not depend on pickup.
 */
@Component
public class IntroPickupLinkSync {

    private final IntroPickupGuideService introPickups;

    public IntroPickupLinkSync(IntroPickupGuideService introPickups) {
        this.introPickups = introPickups;
    }

    @EventListener
    public void onSignedOut(PickupEvents.PickupSignedOutEvent event) {
        introPickups.autoCancelLinked(event.userId(), event.storeId(), event.pickupAt(), cause(event));
    }

    @EventListener
    public void onSignupsDeleted(PickupEvents.PickupSignupsDeletedEvent event) {
        AutoCancelCause cause = event.reason() == PickupEvents.SignupsDeletedReason.LEFT_TEAM
                ? AutoCancelCause.LEFT_TEAM : AutoCancelCause.ALL_FUTURE_DELETED;
        for (PickupEvents.DeletedSignup signup : event.signups()) {
            introPickups.autoCancelLinked(event.userId(), signup.storeId(), signup.pickupAt(), cause);
        }
    }

    /** PU10 move sync (decision 170): linked EinAbs of the moved sign-ups' guides move along. */
    @EventListener
    public void onMoved(PickupEvents.PickupMovedEvent event) {
        if (!event.userIds().isEmpty()) {
            introPickups.moveLinked(event.userIds(), event.storeId(), event.from(), event.to());
        }
    }

    /** SIGNED_OUT (self), AUTOMATION (no actor) or REMOVED (a manager). */
    static AutoCancelCause cause(PickupEvents.PickupSignedOutEvent event) {
        if (event.byUserId() == null) {
            return AutoCancelCause.AUTOMATION;
        }
        return event.self() ? AutoCancelCause.SIGNED_OUT : AutoCancelCause.REMOVED;
    }
}
