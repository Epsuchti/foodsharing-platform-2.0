package network.foodsharing.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A32: delivery after commit (none on rollback), immediate without a transaction, failures never propagate. */
class StompRealtimePublisherTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final RealtimeSessionRegistry sessions = new RealtimeSessionRegistry();
    private final StompRealtimePublisher publisher = new StompRealtimePublisher(template, sessions);
    private final UUID user = UUID.randomUUID();
    private final RealtimeEvent event = new RealtimeEvent(RealtimeEventType.PING, Instant.parse("2026-11-02T09:00:00Z"),
            Map.of("x", 1));

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutTransactionSendsImmediatelyInTheContractShape() {
        publisher.toUsers(Set.of(user), event);
        verify(template).convertAndSendToUser(user.toString(), "/queue/events",
                Map.of("type", "PING", "at", "2026-11-02T09:00:00Z", "payload", Map.of("x", 1)));
    }

    @Test
    void insideATransactionOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.toUsers(Set.of(user), event);
        verify(template, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSendToUser(eq(user.toString()), eq("/queue/events"), any(Object.class));
    }

    @Test
    void rollbackSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.toUsers(Set.of(user), event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(template, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    void sendFailuresAreSwallowedAndPresenceComesFromTheRegistry() {
        doThrow(new IllegalStateException("broker down")).when(template)
                .convertAndSendToUser(anyString(), anyString(), any(Object.class));
        publisher.toUsers(Set.of(user), event);
        sessions.register("s1", user);
        assertThat(publisher.connectedUsers()).containsExactly(user);
    }
}
