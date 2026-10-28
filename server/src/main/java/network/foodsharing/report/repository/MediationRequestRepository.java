package network.foodsharing.report.repository;

import java.util.UUID;
import network.foodsharing.report.domain.MediationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediationRequestRepository extends JpaRepository<MediationRequest, UUID> {
}
