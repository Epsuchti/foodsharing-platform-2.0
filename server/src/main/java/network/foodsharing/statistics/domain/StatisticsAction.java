package network.foodsharing.statistics.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Statistics jobs (subject SYSTEM, codes prefixed STATISTICS_). */
public enum StatisticsAction implements HistoryAction {
    AGGREGATED,
    USER_STATS_REFRESHED;

    @Override
    public String code() {
        return "STATISTICS_" + name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.SYSTEM;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.DAYS_90;
    }
}
