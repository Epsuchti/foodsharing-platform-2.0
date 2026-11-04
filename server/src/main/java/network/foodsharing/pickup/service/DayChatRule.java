package network.foodsharing.pickup.service;

import network.foodsharing.store.domain.UserTeamStatus;

/** PU3 day chat (decision 165): who may open (find or create) the chat of a store's pickup day. */
public final class DayChatRule {

    private DayChatRule() {
    }

    /** Team members of the store (managers included) and people signed up that day. */
    public static boolean mayOpen(UserTeamStatus status, boolean signedUpThatDay) {
        return status == UserTeamStatus.MEMBER || status == UserTeamStatus.COORDINATOR || signedUpThatDay;
    }
}
