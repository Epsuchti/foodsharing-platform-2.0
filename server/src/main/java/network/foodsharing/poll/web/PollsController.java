package network.foodsharing.poll.web;

import network.foodsharing.api.PollsApi;
import network.foodsharing.api.model.Poll;
import network.foodsharing.api.model.PollCreate;
import network.foodsharing.api.model.PollScopeCount;
import network.foodsharing.api.model.PollSummary;
import network.foodsharing.api.model.PollUpdate;
import network.foodsharing.api.model.PollVote;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/** Polls (V1). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class PollsController implements PollsApi {

    @Override
    public ResponseEntity<Void> cancelPoll(UUID pollId) {
        throw ApiException.notImplemented("cancelPoll");
    }

    @Override
    public ResponseEntity<Poll> createPoll(PollCreate pollCreate) {
        throw ApiException.notImplemented("createPoll");
    }

    @Override
    public ResponseEntity<Poll> getPoll(UUID pollId) {
        throw ApiException.notImplemented("getPoll");
    }

    @Override
    public ResponseEntity<List<PollScopeCount>> getPollScopeCounts(UUID regionId) {
        throw ApiException.notImplemented("getPollScopeCounts");
    }

    @Override
    public ResponseEntity<List<PollSummary>> listMyOpenPolls() {
        throw ApiException.notImplemented("listMyOpenPolls");
    }

    @Override
    public ResponseEntity<List<PollSummary>> listRegionPolls(UUID regionId) {
        throw ApiException.notImplemented("listRegionPolls");
    }

    @Override
    public ResponseEntity<Poll> updatePoll(UUID pollId, PollUpdate pollUpdate) {
        throw ApiException.notImplemented("updatePoll");
    }

    @Override
    public ResponseEntity<Poll> votePoll(UUID pollId, PollVote pollVote) {
        throw ApiException.notImplemented("votePoll");
    }
}
