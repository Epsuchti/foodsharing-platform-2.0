package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Anonymous counter for the automation metrics (AU7 key {@code INTRO_PICKUP.DONE}): when intro pickups were done. */
@Service
@Transactional(readOnly = true)
public class IntroPickupMetrics {

    /** One done intro-pickup slot. */
    public record Done(UUID regionId, Instant doneAt) {
    }

    private final IntroPickupSlotRepository slots;

    public IntroPickupMetrics(IntroPickupSlotRepository slots) {
        this.slots = slots;
    }

    public List<Done> done(Collection<UUID> regionIds, Instant from, Instant to) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        return slots.doneIn(regionIds, from, to).stream()
                .map(row -> new Done((UUID) row[0], (Instant) row[1])).toList();
    }
}
