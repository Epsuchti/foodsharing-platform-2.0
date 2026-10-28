package network.foodsharing.achievement.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Achievement history (subject ACHIEVEMENT). */
public enum AchievementAction implements HistoryAction {
    DEFINED,
    UPDATED,
    DELETED,
    AWARDED,
    AWARD_EDITED,
    REVOKED,
    AUTO_AWARDED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.ACHIEVEMENT;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
