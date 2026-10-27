package network.foodsharing.store.domain;

import java.util.Set;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Store chain history (subject = the chain; secondary subject = the KAM). */
public enum StoreChainAction implements HistoryAction {
    CHAIN_CREATED,
    CHAIN_UPDATED,
    FIELD_CHANGED,
    KAM_ADDED,
    KAM_REMOVED;

    private static final Set<String> SHORT_LIVED = Set.of();

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.STORE_CHAIN;
    }

    @Override
    public RetentionPolicy retention() {
        return SHORT_LIVED.contains(name()) ? RetentionPolicy.YEARS_2 : RetentionPolicy.FOREVER;
    }
}
