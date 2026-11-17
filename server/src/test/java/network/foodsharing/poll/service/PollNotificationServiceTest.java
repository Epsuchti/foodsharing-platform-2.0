package network.foodsharing.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.poll.repository.PollVoteRepository;
import network.foodsharing.testsupport.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * V1/N3 poll announcements (e2e v1-polls "scopes count and notifications", decision 180): a started poll is announced
 * once to the eligible members who have not voted; "ending soon" goes only to eligible non-voters of polls that ran
 * at least 30 hours. The repository queries pick polls not announced yet; the service marks them announced.
 */
class PollNotificationServiceTest {

    static final UUID REGION = UUID.randomUUID();
    static final UUID VOTER = UUID.randomUUID();
    static final UUID NOT_VOTED = UUID.randomUUID();

    final PollRepository polls = mock(PollRepository.class);
    final PollVoteRepository votes = mock(PollVoteRepository.class);
    final PollEligibilityService eligibility = mock(PollEligibilityService.class);
    final PollNotifier notifier = mock(PollNotifier.class);
    final HistoryService history = mock(HistoryService.class);
    final Instant now = new FixedClockConfig().clock().instant();
    PollNotificationService service;

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        when(eligibility.eligibleUserIds(REGION, PollScope.FOODSAVERS)).thenReturn(Set.of(VOTER, NOT_VOTED));
        when(polls.startedNotAnnounced(any())).thenReturn(List.of());
        when(polls.endingNotAnnounced(any(), any())).thenReturn(List.of());
        service = new PollNotificationService(polls, votes, eligibility, notifier, history, new FixedClockConfig().clock());
    }

    @Test
    void aStartedPollIsAnnouncedOnceToEligibleMembersWhoHaveNotVoted() {
        Poll poll = poll(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(40)));
        when(polls.startedNotAnnounced(now)).thenReturn(List.of(poll));
        when(votes.voterIds(poll.getId())).thenReturn(List.of(VOTER));

        assertThat(service.notifyDue()).isEqualTo(1);

        assertThat(recipients(NotificationType.POLL_STARTED)).containsExactly(NOT_VOTED);
        assertThat(poll.getStartNotifiedAt()).isEqualTo(now);
        assertThat(poll.getEndNotifiedAt()).isNull();
        verify(polls).save(poll);
    }

    @Test
    void endingSoonRemindsOnlyNonVotersOfPollsThatRanThirtyHours() {
        Poll longPoll = poll(now.minus(Duration.ofHours(30)), now.plus(Duration.ofHours(12)));
        when(polls.endingNotAnnounced(now, now.plus(Duration.ofHours(24)))).thenReturn(List.of(longPoll));
        when(votes.voterIds(longPoll.getId())).thenReturn(List.of(VOTER));

        assertThat(service.notifyDue()).isEqualTo(1);

        assertThat(recipients(NotificationType.POLL_ENDING_SOON)).containsExactly(NOT_VOTED);
        assertThat(longPoll.getEndNotifiedAt()).isEqualTo(now);
    }

    @Test
    void aShortPollGetsNoEndingSoonReminder() {
        Poll shortPoll = poll(now.minus(Duration.ofHours(10)), now.plus(Duration.ofHours(12)));
        when(polls.endingNotAnnounced(any(), any())).thenReturn(List.of(shortPoll));

        assertThat(service.notifyDue()).isZero();

        verify(notifier, never()).notify(any(), any(), any());
        assertThat(shortPoll.getEndNotifiedAt()).isNull();
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> recipients(NotificationType type) {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notifier).notify(any(), eq(type), captor.capture());
        return captor.getValue();
    }

    private static Poll poll(Instant startsAt, Instant endsAt) {
        Poll poll = new Poll();
        poll.setId(UUID.randomUUID());
        poll.setRegionId(REGION);
        poll.setScope(PollScope.FOODSAVERS);
        poll.setStartsAt(startsAt);
        poll.setEndsAt(endsAt);
        return poll;
    }
}
