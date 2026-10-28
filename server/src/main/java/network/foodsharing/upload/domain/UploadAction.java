package network.foodsharing.upload.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Upload history (subject UPLOAD; docs/conventions.md A34). */
public enum UploadAction implements HistoryAction {
    UPLOADED,
    CLAIMED,
    DELETED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.UPLOAD;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.DAYS_90;
    }
}
