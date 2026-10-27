package network.foodsharing.user.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** USER history: the user themself (ORGA via the policy). Ambassadors of the home region follow in milestone 2. */
@Component
public class UserHistoryAccessRule implements HistoryAccessRule {

    @Override
    public SubjectType subjectType() {
        return SubjectType.USER;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return actor != null && actor.userId().equals(subjectId);
    }
}
