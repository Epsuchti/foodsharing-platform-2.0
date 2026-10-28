package network.foodsharing.resource.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Resource mosaic history (subject RESOURCE). */
public enum ResourceAction implements HistoryAction {
    CREATED,
    UPDATED,
    DELETED,
    FAVOURITED,
    UNFAVOURITED,
    CATEGORY_CHANGED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.RESOURCE;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
