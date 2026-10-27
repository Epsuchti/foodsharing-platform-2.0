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
}
