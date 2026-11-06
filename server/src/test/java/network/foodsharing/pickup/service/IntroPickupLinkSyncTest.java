package network.foodsharing.pickup.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.intropickup.service.IntroPickupGuideService;
import network.foodsharing.intropickup.service.IntroPickupGuideService.AutoCancelCause;
import network.foodsharing.pickup.domain.PickupEvents;
import org.junit.jupiter.api.Test;

/** EA16 (decision 169): every way a sign-up goes away cancels the EinAb linked to it, with the right cause. */
class IntroPickupLinkSyncTest {

    private static final UUID STORE = UUID.randomUUID();
    private static final UUID OTHER_STORE = UUID.randomUUID();
    private static final UUID GUIDE = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-11-06T17:30:00Z");

    private final IntroPickupGuideService introPickups = mock(IntroPickupGuideService.class);
    private final IntroPickupLinkSync sync = new IntroPickupLinkSync(introPickups);

    @Test
    void signOutByTheGuideThemselfAManagerOrTheAutomation() {
        sync.onSignedOut(new PickupEvents.PickupSignedOutEvent(STORE, AT, GUIDE, GUIDE, AT));
        verify(introPickups).autoCancelLinked(GUIDE, STORE, AT, AutoCancelCause.SIGNED_OUT);

        sync.onSignedOut(new PickupEvents.PickupSignedOutEvent(OTHER_STORE, AT, GUIDE, UUID.randomUUID(), AT));
        verify(introPickups).autoCancelLinked(GUIDE, OTHER_STORE, AT, AutoCancelCause.REMOVED);

        sync.onSignedOut(new PickupEvents.PickupSignedOutEvent(STORE, AT.plusSeconds(60), GUIDE, null, AT));
        verify(introPickups).autoCancelLinked(GUIDE, STORE, AT.plusSeconds(60), AutoCancelCause.AUTOMATION);
    }

    @Test
    void aMovedPickupMovesTheEinAbsOfItsSignupsAlong() {
        Instant to = AT.plusSeconds(86400);
        List<UUID> moved = List.of(GUIDE, UUID.randomUUID());
        sync.onMoved(new PickupEvents.PickupMovedEvent(STORE, AT, to, moved, UUID.randomUUID()));
        verify(introPickups).moveLinked(moved, STORE, AT, to);

        sync.onMoved(new PickupEvents.PickupMovedEvent(OTHER_STORE, AT, to, List.of(), GUIDE));
        org.mockito.Mockito.verifyNoMoreInteractions(introPickups);
    }

    @Test
    void deletedSignupsCancelEachLinkedEinAb() {
        sync.onSignupsDeleted(new PickupEvents.PickupSignupsDeletedEvent(GUIDE, List.of(
                new PickupEvents.DeletedSignup(STORE, AT), new PickupEvents.DeletedSignup(OTHER_STORE, AT)), GUIDE,
                PickupEvents.SignupsDeletedReason.ALL_FUTURE_DELETED));
        verify(introPickups).autoCancelLinked(GUIDE, STORE, AT, AutoCancelCause.ALL_FUTURE_DELETED);
        verify(introPickups).autoCancelLinked(GUIDE, OTHER_STORE, AT, AutoCancelCause.ALL_FUTURE_DELETED);

        sync.onSignupsDeleted(new PickupEvents.PickupSignupsDeletedEvent(GUIDE, List.of(
                new PickupEvents.DeletedSignup(STORE, AT.plusSeconds(3600))), null,
                PickupEvents.SignupsDeletedReason.LEFT_TEAM));
        verify(introPickups).autoCancelLinked(GUIDE, STORE, AT.plusSeconds(3600), AutoCancelCause.LEFT_TEAM);
    }
}
