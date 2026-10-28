package network.foodsharing.resource.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.resource.domain.ResourceLinks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCategoryNameRepository extends JpaRepository<ResourceLinks.CategoryName, UUID> {

    List<ResourceLinks.CategoryName> findByCategoryIdIn(Collection<UUID> categoryIds);
}
