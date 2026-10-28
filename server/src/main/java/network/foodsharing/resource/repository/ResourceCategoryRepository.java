package network.foodsharing.resource.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.resource.domain.ResourceLinks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCategoryRepository extends JpaRepository<ResourceLinks.Category, UUID> {

    List<ResourceLinks.Category> findAllByOrderBySortOrder();
}
