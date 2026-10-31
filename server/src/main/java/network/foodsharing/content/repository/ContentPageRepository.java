package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.content.domain.ContentPage;
import network.foodsharing.content.domain.ContentPageKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentPageRepository extends JpaRepository<ContentPage, UUID> {

    Optional<ContentPage> findBySlug(String slug);

    List<ContentPage> findByKindOrderBySortOrderAscSlugAscIdAsc(ContentPageKind kind);

    List<ContentPage> findAllByOrderByKindAscSortOrderAscSlugAscIdAsc();
}
