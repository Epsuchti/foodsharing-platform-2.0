package network.foodsharing.basket.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.basket.domain.BasketRequestEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketRequestEntryRepository extends JpaRepository<BasketRequestEntry, UUID> {

    List<BasketRequestEntry> findByBasketIdOrderByRequestedAt(UUID basketId);

    Optional<BasketRequestEntry> findByBasketIdAndRequesterId(UUID basketId, UUID requesterId);

    List<BasketRequestEntry> findByRequesterIdOrderByRequestedAtDesc(UUID requesterId);

    List<BasketRequestEntry> findByBasketIdInAndStatusAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
            Collection<UUID> basketIds, network.foodsharing.basket.domain.BasketRequestStatus status, Instant from,
            Instant to);
}
