package network.foodsharing.history.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** SYSTEM entries (mail outbox, jobs, blocked registrations) are readable by ORGA only (handled by the policy). */
@Component
public class SystemHistoryAccessRule implements HistoryAccessRule {

    @Override
    public SubjectType subjectType() {
        return SubjectType.SYSTEM;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return false;
    }
}
