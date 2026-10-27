package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.content.domain.ContentPageI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentPageI18nRepository extends JpaRepository<ContentPageI18n, UUID> {

    List<ContentPageI18n> findByOwnerId(UUID ownerId);

    List<ContentPageI18n> findByOwnerIdIn(Collection<UUID> ownerIds);
}
