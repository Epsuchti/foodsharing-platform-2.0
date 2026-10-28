package network.foodsharing.forum.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Forum history and moderation log (subject THREAD; docs/conventions.md A37). */
public enum ForumAction implements HistoryAction {
    THREAD_CREATED,
    THREAD_ACTIVATED,
    THREAD_RENAMED,
    THREAD_CLOSED,
    THREAD_OPENED,
    THREAD_STICKY_CHANGED,
    THREAD_DELETED,
    POST_CREATED,
    POST_EDITED,
    POST_DELETED,
    POST_HIDDEN,
    POST_UNHIDDEN;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.THREAD;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
