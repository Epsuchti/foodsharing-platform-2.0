package network.foodsharing.resource.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.resource.domain.ResourceLinks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceFavouriteRepository extends JpaRepository<ResourceLinks.Favourite, UUID> {

    Optional<ResourceLinks.Favourite> findByUserIdAndResourceId(UUID userId, UUID resourceId);

    List<ResourceLinks.Favourite> findByUserId(UUID userId);

    List<ResourceLinks.Favourite> findByUserIdAndResourceIdIn(UUID userId, Collection<UUID> resourceIds);

    List<ResourceLinks.Favourite> findByResourceId(UUID resourceId);
}
