package network.foodsharing.poll.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.domain.PollAction;
import network.foodsharing.poll.domain.PollVote;
import network.foodsharing.poll.repository.PollOptionRepository;
import network.foodsharing.poll.repository.PollOptionValueRepository;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.poll.repository.PollVoteRepository;
import network.foodsharing.poll.service.PollPermissionService.PollContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casting a vote (V1). Anonymous: {@code poll_vote} records who voted, the values only increase the aggregates in
 * {@code poll_option_value}; the {@code VOTE_CAST} history entry carries no values.
 */
@Service
public class VoteService {

    private final PollRepository polls;
    private final PollOptionRepository options;
    private final PollOptionValueRepository values;
    private final PollVoteRepository votes;
    private final PollPermissionService access;
    private final PollEligibilityService eligibility;
    private final PollService pollService;
    private final PollNotifier notifier;
    private final HistoryService history;
    private final Clock clock;

    public VoteService(PollRepository polls, PollOptionRepository options, PollOptionValueRepository values,
                       PollVoteRepository votes, PollPermissionService access, PollEligibilityService eligibility,
                       PollService pollService, PollNotifier notifier, HistoryService history, Clock clock) {
        this.polls = polls;
        this.options = options;
        this.values = values;
        this.votes = votes;
        this.access = access;
        this.eligibility = eligibility;
        this.pollService = pollService;
        this.notifier = notifier;
        this.history = history;
        this.clock = clock;
    }

    /**
     * @param ballot option index → value
     * @throws ApiException 404 POLL_NOT_FOUND, 403 ACCESS_DENIED / NOT_ELIGIBLE, 409 POLL_NOT_OPEN / ALREADY_VOTED,
     *                      400 INVALID_VOTE
     */
    @Transactional
    public PollViews.PollDetails vote(UUID pollId, Map<Integer, Integer> ballot) {
        PollContext context = access.visiblePollForUpdate(pollId);
        Poll poll = context.poll();
        UUID me = context.actor().userId();
        if (pollService.status(poll) != PollRules.Status.RUNNING) {
            throw ApiException.conflict(ApiErrorCode.POLL_NOT_OPEN);
        }
        if (votes.existsByPollIdAndUserId(pollId, me)) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_VOTED);
        }
        if (!context.actor().member() || !eligibility.eligibleUserIds(poll.getRegionId(), poll.getScope()).contains(me)) {
            throw ApiException.forbidden(ApiErrorCode.NOT_ELIGIBLE);
        }
        int optionCount = options.findByPollIdOrderByOptionIndex(pollId).size();
        if (!PollRules.validVote(poll.getType(), optionCount, ballot)) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_VOTE);
        }
        PollVote vote = new PollVote();
        vote.setPollId(pollId);
        vote.setUserId(me);
        vote.setVotedAt(clock.instant());
        votes.save(vote);
        ballot.forEach((index, value) -> values.increment(pollId, index, value));
        polls.incrementVoteCount(pollId);
        history.record(history.event(PollAction.VOTE_CAST, pollId).region(poll.getRegionId()).build());
        notifier.removeOpenBells(pollId, me);
        Poll fresh = polls.findById(pollId).orElseThrow();
        notifier.voteCast(fresh, fresh.getVoteCount());
        return pollService.details(new PollContext(fresh, context.actor()));
    }
}
