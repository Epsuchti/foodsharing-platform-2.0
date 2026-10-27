package network.foodsharing.verification.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.verification.domain.WalletPass;
import network.foodsharing.verification.domain.WalletType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletPassRepository extends JpaRepository<WalletPass, UUID> {

    Optional<WalletPass> findByUserIdAndWalletType(UUID userId, WalletType walletType);
}
