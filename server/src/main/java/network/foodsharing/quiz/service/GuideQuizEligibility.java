package network.foodsharing.quiz.service;

import java.util.UUID;

/**
 * Port (Q4, decision 168): may a user try the EINAB_GUIDE quiz as far as pickups go? Implemented by the intropickup
 * module, so the quiz module needs no intropickup imports.
 */
public interface GuideQuizEligibility {

    /**
     * @param pickupCount confirmed past store pickups
     * @param minPickups  threshold of the home region (global default without one)
     * @param guide       currently holds CAN_GIVE_INTRO_PICKUPS in some region (skips the threshold)
     */
    record Status(int pickupCount, int minPickups, boolean guide) {

        public boolean reached() {
            return guide || pickupCount >= minPickups;
        }
    }

    Status of(UUID userId);
}
