package network.foodsharing.basket.service;

import java.util.UUID;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** A basket's history: its owner and ORGA. */
@Component
public class BasketHistoryAccessRule implements HistoryAccessRule {

    private final BasketRepository baskets;
    private final PermissionService permissions;

    public BasketHistoryAccessRule(BasketRepository baskets, PermissionService permissions) {
        this.baskets = baskets;
        this.permissions = permissions;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.BASKET;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return permissions.isOrga(actor)
                || baskets.findById(subjectId).map(b -> b.getOwnerId().equals(actor.userId())).orElse(false);
    }
}
