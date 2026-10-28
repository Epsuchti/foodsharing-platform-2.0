package network.foodsharing.pickup.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Pickup times in mails (the client formats {@code pickupAt} itself). */
public final class PickupFormat {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private PickupFormat() {
    }

    public static String when(Instant at, ZoneId zone) {
        return WHEN.format(at.atZone(zone));
    }
}
