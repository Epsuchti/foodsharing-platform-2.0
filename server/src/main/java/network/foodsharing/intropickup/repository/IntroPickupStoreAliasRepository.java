package network.foodsharing.intropickup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupStoreAlias;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntroPickupStoreAliasRepository extends JpaRepository<IntroPickupStoreAlias, UUID> {

    List<IntroPickupStoreAlias> findByRegionIdOrderByAliasKeyAsc(UUID regionId);

    Optional<IntroPickupStoreAlias> findByRegionIdAndAliasKey(UUID regionId, String aliasKey);

    Optional<IntroPickupStoreAlias> findByIdAndRegionId(UUID id, UUID regionId);
}
