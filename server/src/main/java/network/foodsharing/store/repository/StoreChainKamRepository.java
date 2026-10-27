package network.foodsharing.store.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.store.domain.StoreChainKam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreChainKamRepository extends JpaRepository<StoreChainKam, UUID> {

    List<StoreChainKam> findByChainId(UUID chainId);

    List<StoreChainKam> findByUserId(UUID userId);

    boolean existsByChainIdAndUserId(UUID chainId, UUID userId);
}
