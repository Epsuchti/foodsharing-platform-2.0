package network.foodsharing.poll.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PollsApi;
import network.foodsharing.api.model.Poll;
import network.foodsharing.api.model.PollCreate;
import network.foodsharing.api.model.PollScopeCount;
import network.foodsharing.api.model.PollSummary;
import network.foodsharing.api.model.PollUpdate;
import network.foodsharing.api.model.PollVote;
import network.foodsharing.poll.mapper.PollMapper;
import network.foodsharing.poll.service.PollService;
import network.foodsharing.poll.service.VoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Polls (V1). */
@RestController
public class PollsController implements PollsApi {

    private final PollService polls;
    private final VoteService votes;
    private final PollMapper mapper;

    public PollsController(PollService polls, VoteService votes, PollMapper mapper) {
        this.polls = polls;
        this.votes = votes;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Void> cancelPoll(UUID pollId) {
        polls.cancel(pollId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Poll> createPoll(PollCreate pollCreate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toPoll(polls.create(PollMapper.toDraft(pollCreate),
                Boolean.TRUE.equals(pollCreate.getNotifyVoters()))));
    }

    @Override
    public ResponseEntity<Poll> getPoll(UUID pollId) {
        return ResponseEntity.ok(mapper.toPoll(polls.get(pollId)));
    }

    @Override
    public ResponseEntity<List<PollScopeCount>> getPollScopeCounts(UUID regionId) {
        return ResponseEntity.ok(PollMapper.toScopeCounts(polls.scopeCounts(regionId)));
    }

    @Override
    public ResponseEntity<List<PollSummary>> listMyOpenPolls() {
        return ResponseEntity.ok(PollMapper.toSummaries(polls.listMyOpen()));
    }

    @Override
    public ResponseEntity<List<PollSummary>> listRegionPolls(UUID regionId) {
        return ResponseEntity.ok(PollMapper.toSummaries(polls.listRegion(regionId)));
    }

    @Override
    public ResponseEntity<Poll> updatePoll(UUID pollId, PollUpdate pollUpdate) {
        return ResponseEntity.ok(mapper.toPoll(polls.update(pollId, PollMapper.toChange(pollUpdate))));
    }

    @Override
    public ResponseEntity<Poll> votePoll(UUID pollId, PollVote pollVote) {
        return ResponseEntity.ok(mapper.toPoll(votes.vote(pollId, PollMapper.toBallot(pollVote.getValues()))));
    }
}
