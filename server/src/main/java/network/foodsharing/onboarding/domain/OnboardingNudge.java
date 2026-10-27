package network.foodsharing.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A nudge sent to a newcomer. */
@Getter
@Setter
@Entity
@Table(name = "onboarding_nudge")
public class OnboardingNudge extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
