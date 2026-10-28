package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.content.domain.ReleaseNoteI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseNoteI18nRepository extends JpaRepository<ReleaseNoteI18n, UUID> {

    List<ReleaseNoteI18n> findByOwnerId(UUID ownerId);

    List<ReleaseNoteI18n> findByOwnerIdIn(Collection<UUID> ownerIds);
}
