package network.foodsharing.poll.service;

import java.time.Instant;
import java.util.List;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.domain.PollOption;
import network.foodsharing.poll.domain.PollOptionValue;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.poll.domain.PollType;

/** Read models and inputs of the poll services. */
public final class PollViews {

    private PollViews() {
    }

    /** Create input. */
    public record PollDraft(java.util.UUID regionId, String name, String description, PollType type, PollScope scope,
                            Instant startsAt, Instant endsAt, List<String> options, boolean shuffleOptions) {
    }

    /** Update input; null = unchanged. */
    public record PollChange(String name, String description, List<String> options, Boolean shuffleOptions) {
    }

    /** A poll in a listing. */
    public record PollItem(Poll poll, PollRules.Status status, boolean voted, PollRules.Actor actor) {
    }

    /** One poll with options; {@code values} empty unless results may be shown. */
    public record PollDetails(PollItem item, List<PollOption> options, List<PollOptionValue> values,
                              boolean mayVote) {
    }
}
