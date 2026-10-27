package network.foodsharing.region.mapper;

import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.UnitMembership;
import network.foodsharing.api.model.UnitType;
import network.foodsharing.api.model.WorkingGroupFunction;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import org.springframework.stereotype.Component;

/** Region entities ↔ generated models (T11 basics; T12 adds the region page models). */
@Component
public class RegionMapper {

    public RegionRef toRef(Region region) {
        RegionRef ref = new RegionRef();
        ref.setId(region.getId());
        ref.setSlug(region.getSlug());
        ref.setName(region.getName());
        ref.setType(UnitType.fromValue(region.getType().name()));
        ref.setParentId(region.getParentId());
        return ref;
    }

    public UnitMembership toUnit(MembershipService.Unit unit) {
        Region region = unit.region();
        UnitMembership membership = new UnitMembership();
        membership.setId(region.getId());
        membership.setName(region.getName());
        membership.setSlug(region.getSlug());
        membership.setType(UnitType.fromValue(region.getType().name()));
        membership.setParentId(region.getParentId());
        membership.setMember(unit.member());
        membership.setAdmin(unit.admin());
        membership.setHomeRegion(unit.homeRegion());
        membership.setFunction(unit.function() == null ? null : WorkingGroupFunction.fromValue(unit.function().name()));
        return membership;
    }
}
