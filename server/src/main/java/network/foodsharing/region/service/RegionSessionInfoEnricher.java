package network.foodsharing.region.service;

import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adds the user's regions and working groups to the session info (docs/conventions.md A11). */
@Component
@Order(10)
public class RegionSessionInfoEnricher implements SessionInfoEnricher {

    private final MembershipService memberships;

    public RegionSessionInfoEnricher(MembershipService memberships) {
        this.memberships = memberships;
    }

    @Override
    public void enrich(SessionPrincipal principal, SessionInfoBuilder builder) {
        for (MembershipService.Unit unit : memberships.units(principal.userId())) {
            builder.addUnit(new SessionInfoBuilder.Unit(unit.region().getId(), unit.region().getName(),
                    unit.region().getSlug(), unit.region().getType().name(), unit.region().getParentId(),
                    unit.member(), unit.admin(), unit.homeRegion(),
                    unit.function() == null ? null : unit.function().name()));
        }
    }
}
