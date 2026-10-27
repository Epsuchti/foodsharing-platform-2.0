package network.foodsharing.history.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * Decides who may read the history of a subject: ORGA always; otherwise the {@link HistoryAccessRule} bean of the
 * subject type (one per type, registered by the owning module); no rule = no access.
 */
@Component
public class HistoryAccessPolicy {

    private final Map<SubjectType, HistoryAccessRule> rules = new EnumMap<>(SubjectType.class);
    private final PermissionService permissionService;

    public HistoryAccessPolicy(List<HistoryAccessRule> rules, PermissionService permissionService) {
        this.permissionService = permissionService;
        for (HistoryAccessRule rule : rules) {
            HistoryAccessRule previous = this.rules.put(rule.subjectType(), rule);
            if (previous != null) {
                throw new IllegalStateException("Two history access rules for " + rule.subjectType() + ": "
                        + previous.getClass().getName() + ", " + rule.getClass().getName());
            }
        }
    }

    public boolean canRead(SessionPrincipal actor, SubjectType subjectType, UUID subjectId) {
        if (actor == null || subjectType == null || subjectId == null) {
            return false;
        }
        if (permissionService.isOrga(actor)) {
            return true;
        }
        HistoryAccessRule rule = rules.get(subjectType);
        return rule != null && rule.canRead(actor, subjectId);
    }
}
