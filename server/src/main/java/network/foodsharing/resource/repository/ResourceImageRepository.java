package network.foodsharing.resource.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.resource.domain.ResourceLinks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceImageRepository extends JpaRepository<ResourceLinks.Image, UUID> {

    List<ResourceLinks.Image> findByResourceIdInOrderBySortOrderAscIdAsc(Collection<UUID> resourceIds);

    List<ResourceLinks.Image> findByResourceId(UUID resourceId);

    List<ResourceLinks.Image> findByUploadId(UUID uploadId);
}
