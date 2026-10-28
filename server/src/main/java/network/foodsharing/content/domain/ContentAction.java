package network.foodsharing.content.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Content and legal document history (subject = the page / document version). */
public enum ContentAction implements HistoryAction {
    PAGE_CREATED,
    PAGE_UPDATED,
    PAGE_PUBLISHED,
    LEGAL_VERSION_PUBLISHED,
    // M6 additions (docs/plan-m6.md B1)
    BLOG_CREATED,
    BLOG_UPDATED,
    BLOG_PUBLISHED,
    BLOG_UNPUBLISHED,
    BLOG_DELETED,
    RELEASE_NOTE_CREATED,
    RELEASE_NOTE_UPDATED,
    RELEASE_NOTE_DELETED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.CONTENT;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
