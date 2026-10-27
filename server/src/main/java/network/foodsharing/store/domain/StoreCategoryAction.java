package network.foodsharing.store.domain;

import java.util.Set;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Category / grocery administration (system subject). */
public enum StoreCategoryAction implements HistoryAction {
    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    CATEGORY_MERGED,
    CATEGORY_DELETED,
    GROCERY_CREATED,
    GROCERY_UPDATED,
    GROCERY_MERGED,
    GROCERY_DELETED;

    private static final Set<String> SHORT_LIVED = Set.of();

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.SYSTEM;
    }

    @Override
    public RetentionPolicy retention() {
        return SHORT_LIVED.contains(name()) ? RetentionPolicy.YEARS_2 : RetentionPolicy.FOREVER;
    }
}
