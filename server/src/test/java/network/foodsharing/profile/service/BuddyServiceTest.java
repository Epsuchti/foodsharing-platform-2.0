package network.foodsharing.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.profile.domain.Buddy;
import network.foodsharing.user.domain.BuddyStatus;
import network.foodsharing.user.domain.User;
import network.foodsharing.profile.repository.BuddyRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A15 buddies: a request rings the other person, a request back confirms both directions, nobody befriends
 * themselves, and removing (withdraw, decline, end) clears both directions and the request bells.
 */
class BuddyServiceTest {

    private final List<Buddy> rows = new ArrayList<>();
    private final BuddyRepository buddies = mock(BuddyRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final UserService users = mock(UserService.class);
    private final ProfileNotifier notifier = mock(ProfileNotifier.class);
    private final BuddyService service = new BuddyService(buddies, actors, users, notifier,
            mock(HistoryService.class, RETURNS_DEEP_STUBS));

    private final UUID guide = UUID.randomUUID();
    private final UUID orga = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(buddies.findByUserIdAndBuddyId(any(), any())).thenAnswer(call -> rows.stream()
                .filter(b -> b.getUserId().equals(call.getArgument(0)) && b.getBuddyId().equals(call.getArgument(1)))
                .findFirst());
        when(buddies.save(any())).thenAnswer(call -> {
            Buddy row = call.getArgument(0);
            if (!rows.contains(row)) {
                rows.add(row);
            }
            return row;
        });
        org.mockito.Mockito.doAnswer(call -> rows.remove((Buddy) call.getArgument(0))).when(buddies).delete(any());
        for (UUID id : List.of(guide, orga)) {
            User user = new User();
            user.setId(id);
            user.setDisplayName(id.equals(guide) ? "Julien Favre" : "Daniel Huber");
            when(users.require(id)).thenReturn(user);
            when(users.requireActive(id)).thenReturn(user);
        }
    }

    private void as(UUID user) {
        when(actors.requireUserId()).thenReturn(user);
    }

    @Test
    void aRequestRingsTheOtherPersonAndARequestBackConfirmsBoth() {
        as(guide);
        assertThat(service.request(orga)).isEqualTo(BuddyService.Relation.REQUESTED_BY_ME);
        verify(notifier).buddyRequested(guide, "Julien Favre", orga);
        assertThat(service.relation(orga, guide)).isEqualTo(BuddyService.Relation.REQUESTED_BY_THEM);
        // asking again changes nothing
        assertThat(service.request(orga)).isEqualTo(BuddyService.Relation.REQUESTED_BY_ME);

        as(orga);
        assertThat(service.request(guide)).isEqualTo(BuddyService.Relation.CONFIRMED);
        assertThat(rows).extracting(Buddy::getStatus).containsOnly(BuddyStatus.CONFIRMED).hasSize(2);
        verify(notifier).buddyRequestDone(guide, orga);
    }

    @Test
    void nobodyBefriendsThemselves() {
        as(guide);
        assertThatThrownBy(() -> service.request(guide)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.BUDDY_SELF));
        verify(buddies, never()).save(any());
    }

    @Test
    void removingAPendingRequestClearsItAndItsBell() {
        as(guide);
        service.request(orga);
        as(orga);
        service.remove(guide);
        assertThat(rows).isEmpty();
        verify(notifier).buddyRequestDone(orga, guide);
        assertThat(service.relation(guide, orga)).isEqualTo(BuddyService.Relation.NONE);
        // removing again is a no-op
        service.remove(guide);
        assertThat(service.relation(orga, guide)).isEqualTo(BuddyService.Relation.NONE);
    }
}
