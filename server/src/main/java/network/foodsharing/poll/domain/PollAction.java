package network.foodsharing.poll.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Poll history (subject POLL). VOTE_CAST never carries the chosen values. */
public enum PollAction implements HistoryAction {
    CREATED,
    UPDATED,
    CANCELLED,
    VOTE_CAST,
    NOTIFIED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.POLL;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
