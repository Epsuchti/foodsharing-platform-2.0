package network.foodsharing.resource.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.resource.domain.ResourceLinks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCategoryLinkRepository extends JpaRepository<ResourceLinks.CategoryLink, UUID> {

    List<ResourceLinks.CategoryLink> findByResourceIdIn(Collection<UUID> resourceIds);

    List<ResourceLinks.CategoryLink> findByResourceId(UUID resourceId);
}
