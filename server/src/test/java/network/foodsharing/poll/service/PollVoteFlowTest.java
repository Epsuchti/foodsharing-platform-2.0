package network.foodsharing.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.domain.PollAction;
import network.foodsharing.poll.domain.PollOption;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.poll.domain.PollType;
import network.foodsharing.poll.repository.PollOptionRepository;
import network.foodsharing.poll.repository.PollOptionValueRepository;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.poll.repository.PollVoteRepository;
import network.foodsharing.poll.service.PollPermissionService.PollContext;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * V1 service paths without a database (formerly PollFlowIT): one vote per person while the poll runs and only in the
 * scope; the history keeps that someone voted, never what (secret ballot); voting clears one's open poll bells; a
 * cancel clears the open bells and tells the eligible members except the canceller; AMBASSADORS are the
 * administrators of the region's subtree.
 */
class PollVoteFlowTest {

    static final Instant NOW = Instant.parse("2026-11-02T11:00:00Z");
    static final UUID REGION = UUID.randomUUID();
    static final UUID ME = UUID.randomUUID();
    static final PollRules.Actor MEMBER = new PollRules.Actor(ME, false, true, true, false, false, false);

    PollRepository polls = mock(PollRepository.class);
    PollOptionRepository options = mock(PollOptionRepository.class);
    PollOptionValueRepository values = mock(PollOptionValueRepository.class);
    PollVoteRepository votes = mock(PollVoteRepository.class);
    PollPermissionService access = mock(PollPermissionService.class);
    PollEligibilityService eligibility = mock(PollEligibilityService.class);
    PollService pollService = mock(PollService.class);
    PollNotifier notifier = mock(PollNotifier.class);
    HistoryService history = mock(HistoryService.class);
    VoteService service = new VoteService(polls, options, values, votes, access, eligibility, pollService, notifier,
            history, Clock.fixed(NOW, ZoneOffset.UTC));
    Poll poll = new Poll();

    @BeforeEach
    void setUp() {
        poll.setId(UUID.randomUUID());
        poll.setRegionId(REGION);
        poll.setType(PollType.SELECT_ONE);
        poll.setScope(PollScope.FOODSAVERS);
        poll.setAuthorId(UUID.randomUUID());
        when(access.visiblePollForUpdate(poll.getId())).thenReturn(new PollContext(poll, MEMBER));
        when(pollService.status(poll)).thenReturn(PollRules.Status.RUNNING);
        when(eligibility.eligibleUserIds(REGION, PollScope.FOODSAVERS)).thenReturn(Set.of(ME));
        when(options.findByPollIdOrderByOptionIndex(poll.getId())).thenReturn(List.of(new PollOption(),
                new PollOption(), new PollOption()));
        when(polls.findById(poll.getId())).thenReturn(Optional.of(poll));
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
    }

    private static ApiErrorCode code(Throwable error) {
        return ((ApiException) error).getCode();
    }

    @Test
    void aVoteIsCountedOnceAndTheHistoryKeepsNoValues() {
        service.vote(poll.getId(), Map.of(1, 1));

        verify(values).increment(poll.getId(), 1, 1);
        verify(polls).incrementVoteCount(poll.getId());
        verify(notifier).removeOpenBells(poll.getId(), ME);
        ArgumentCaptor<HistoryEvent> entry = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(history).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(PollAction.VOTE_CAST);
        assertThat(entry.getValue().metadata()).isEmpty();
        assertThat(entry.getValue().newValue()).isNull();

        when(votes.existsByPollIdAndUserId(poll.getId(), ME)).thenReturn(true);
        assertThatThrownBy(() -> service.vote(poll.getId(), Map.of(0, 1)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.ALREADY_VOTED));
    }

    @Test
    void votesOnlyWhileRunningOnlyInScopeAndOnlyValidOnes() {
        when(pollService.status(poll)).thenReturn(PollRules.Status.UPCOMING);
        assertThatThrownBy(() -> service.vote(poll.getId(), Map.of(0, 1)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.POLL_NOT_OPEN));

        when(pollService.status(poll)).thenReturn(PollRules.Status.RUNNING);
        when(eligibility.eligibleUserIds(REGION, PollScope.FOODSAVERS)).thenReturn(Set.of());
        assertThatThrownBy(() -> service.vote(poll.getId(), Map.of(0, 1)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.NOT_ELIGIBLE));

        when(eligibility.eligibleUserIds(REGION, PollScope.FOODSAVERS)).thenReturn(Set.of(ME));
        assertThatThrownBy(() -> service.vote(poll.getId(), Map.of(0, 1, 1, 1)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.INVALID_VOTE));
        verify(values, never()).increment(any(), anyInt(), anyInt());
    }

    @Test
    void aCancelClearsOpenBellsAndTellsTheEligibleExceptTheCanceller() {
        PollEligibilityService scopes = mock(PollEligibilityService.class);
        UUID other = UUID.randomUUID();
        poll.setAuthorId(ME);
        poll.setStartsAt(NOW.minusSeconds(3600));
        poll.setEndsAt(NOW.plusSeconds(3600));
        when(access.visiblePoll(poll.getId())).thenReturn(new PollContext(poll, MEMBER));
        when(notifier.votersNotified(poll)).thenReturn(true);
        when(scopes.eligibleUserIds(REGION, PollScope.FOODSAVERS)).thenReturn(Set.of(ME, other));
        PollService real = new PollService(polls, options, values, votes, access, scopes, notifier,
                mock(RegionService.class), mock(CurrentActorService.class), history, Clock.fixed(NOW, ZoneOffset.UTC));

        real.cancel(poll.getId());

        assertThat(poll.getCancelledAt()).isEqualTo(NOW);
        assertThat(poll.getCancelledBy()).isEqualTo(ME);
        verify(notifier).removeOpenBells(poll.getId());
        verify(notifier).notify(poll, NotificationType.POLL_CANCELLED, Set.of(other));
        assertThatThrownBy(() -> real.cancel(poll.getId()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.POLL_NOT_CANCELLABLE));
    }

    @Test
    void ambassadorsAreTheAdministratorsOfTheRegionSubtree() {
        RegionService regions = mock(RegionService.class);
        UserService users = mock(UserService.class);
        Region city = region(REGION, UnitType.CITY);
        Region district = region(UUID.randomUUID(), UnitType.DISTRICT);
        Region group = region(UUID.randomUUID(), UnitType.WORKING_GROUP);
        UUID ambassador = UUID.randomUUID();
        UUID districtAmbassador = UUID.randomUUID();
        UUID groupAdmin = UUID.randomUUID();
        when(regions.require(REGION)).thenReturn(city);
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION, district.getId(), group.getId()));
        when(regions.findAll(any())).thenReturn(List.of(city, district, group));
        when(regions.adminIds(REGION)).thenReturn(Set.of(ambassador));
        when(regions.adminIds(district.getId())).thenReturn(Set.of(districtAmbassador));
        when(regions.adminIds(group.getId())).thenReturn(Set.of(groupAdmin));
        when(users.findActive(org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>any())).thenAnswer(call -> ((java.util.Collection<UUID>) call.getArgument(0)).stream()
                .map(PollVoteFlowTest::user).toList());

        assertThat(new PollEligibilityService(regions, users).eligibleUserIds(REGION, PollScope.AMBASSADORS))
                .containsExactlyInAnyOrder(ambassador, districtAmbassador);
    }

    static Region region(UUID id, UnitType type) {
        Region region = new Region();
        region.setId(id);
        region.setType(type);
        return region;
    }

    static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
