package network.foodsharing.region.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.security.WorkingGroupFunction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionFunctionRepository extends JpaRepository<RegionFunction, UUID> {

    Optional<RegionFunction> findByRegionIdAndFunction(UUID regionId, WorkingGroupFunction function);

    List<RegionFunction> findByRegionId(UUID regionId);

    List<RegionFunction> findByGroupIdIn(Collection<UUID> groupIds);

    Optional<RegionFunction> findFirstByGroupId(UUID groupId);

    List<RegionFunction> findByFunction(WorkingGroupFunction function);
}
