package network.foodsharing.pickup.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Guards for moving one pickup to another time of the same store (PU10, decision 170). Pure: the caller loads the
 * pickup at the old and at the new time. Eligibility and automation rules of the moved sign-ups are not re-checked.
 */
public final class PickupMoveRule {

    /** Maximum length of the optional message to the people signed up (contract {@code PickupMove.message}). */
    public static final int MAX_MESSAGE = 500;

    /** Why a move is refused, in the order the checks run. */
    public enum Refusal {
        ACCESS_DENIED(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED),
        NOT_FOUND(HttpStatus.NOT_FOUND, ApiErrorCode.PICKUP_NOT_FOUND),
        IN_PAST(HttpStatus.BAD_REQUEST, ApiErrorCode.PICKUP_IN_PAST),
        SAME_TIME(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED),
        MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED),
        TIME_TAKEN(HttpStatus.CONFLICT, ApiErrorCode.PICKUP_TIME_TAKEN);

        private final HttpStatus status;
        private final ApiErrorCode code;

        Refusal(HttpStatus status, ApiErrorCode code) {
            this.status = status;
            this.code = code;
        }

        public ApiException exception() {
            return new ApiException(status, code, List.of(), Map.of());
        }
    }

    private PickupMoveRule() {
    }

    /**
     * @param mayEditPickups the actor's right in the store (store managers, coordinators; same as editPickup)
     * @param current        the pickup at the old time, null when the store has none there
     * @param newPickupAt    the requested new time (null = missing)
     * @param atNew          the pickup already at the new time, null when there is none
     * @param message        the stripped optional message, null when blank
     * @return the first refusal, empty when the move may go ahead
     */
    public static Optional<Refusal> check(boolean mayEditPickups, PickupSchedule.Slot current, Instant newPickupAt,
                                          PickupSchedule.Slot atNew, Instant now, String message) {
        if (!mayEditPickups) {
            return Optional.of(Refusal.ACCESS_DENIED);
        }
        if (current == null) {
            return Optional.of(Refusal.NOT_FOUND);
        }
        if (!current.pickupAt().isAfter(now) || newPickupAt == null || !newPickupAt.isAfter(now)) {
            return Optional.of(Refusal.IN_PAST);
        }
        if (newPickupAt.equals(current.pickupAt())) {
            return Optional.of(Refusal.SAME_TIME);
        }
        if (message != null && message.length() > MAX_MESSAGE) {
            return Optional.of(Refusal.MESSAGE_TOO_LONG);
        }
        if (taken(atNew)) {
            return Optional.of(Refusal.TIME_TAKEN);
        }
        return Optional.empty();
    }

    /** A pickup with slots or with sign-ups occupies its time; a cancelled one without sign-ups does not. */
    public static boolean taken(PickupSchedule.Slot atNew) {
        return atNew != null && (atNew.slotCount() > 0 || atNew.occupied() > 0);
    }

    /** A regular occurrence stays cancelled at the old time; a one-time pickup's row goes away. */
    public static boolean keepCancelledOccurrence(PickupSchedule.Slot current) {
        return current.regular();
    }

    /** The moved pickup keeps its slots (including added extra slots), never fewer than its sign-ups. */
    public static int slotCountAfterMove(PickupSchedule.Slot current) {
        return Math.max(current.slotCount(), current.occupied());
    }
}
