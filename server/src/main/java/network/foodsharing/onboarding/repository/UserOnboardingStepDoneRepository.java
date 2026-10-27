package network.foodsharing.onboarding.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.onboarding.domain.UserOnboardingStepDone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOnboardingStepDoneRepository extends JpaRepository<UserOnboardingStepDone, UUID> {

    List<UserOnboardingStepDone> findByUserId(UUID userId);

    boolean existsByUserIdAndStepId(UUID userId, UUID stepId);
}
