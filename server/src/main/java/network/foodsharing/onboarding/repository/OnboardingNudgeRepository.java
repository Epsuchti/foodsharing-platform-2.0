package network.foodsharing.onboarding.repository;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.onboarding.domain.OnboardingNudge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingNudgeRepository extends JpaRepository<OnboardingNudge, UUID> {

    boolean existsByUserIdAndKindAndSentAtAfter(UUID userId, String kind, Instant after);
}
