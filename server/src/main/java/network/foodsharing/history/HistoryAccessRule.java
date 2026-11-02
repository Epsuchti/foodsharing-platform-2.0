package network.foodsharing.history;

import java.util.UUID;
import network.foodsharing.security.SessionPrincipal;

/**
 * Who may read the history of a subject type. Each module registers one bean per subject type it owns
 * (e.g. the user module for {@link SubjectType#USER}). ORGA may always read; without a rule nobody else may.
 */
public interface HistoryAccessRule {

    SubjectType subjectType();

    boolean canRead(SessionPrincipal actor, UUID subjectId);

    /**
     * Whether a reader of the subject also sees the recorded values ({@code oldValue}, {@code newValue},
     * {@code reason}); when false they are cleared in every history read (lists, own activity, export). ORGA always
     * sees them.
     */
    default boolean mayReadValues(SessionPrincipal actor, UUID subjectId) {
        return true;
    }
}
