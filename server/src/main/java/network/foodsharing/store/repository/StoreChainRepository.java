package network.foodsharing.store.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.store.domain.StoreChain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreChainRepository extends JpaRepository<StoreChain, UUID> {

    List<StoreChain> findAllByOrderByNameAsc();
}
