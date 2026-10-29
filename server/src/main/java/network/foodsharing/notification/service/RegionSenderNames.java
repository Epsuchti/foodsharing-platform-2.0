package network.foodsharing.notification.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Region system sender (AU11, plan §14.2 "region message account connections"): automated messages sent on behalf of
 * a region ({@link NotificationRequest#senderRegionId()}, store automations) carry the region's name. Mails get the
 * From display name {@code foodsharing <Region>} and a footer line, Telegram messages a signature line. Implemented
 * by the region module (the notification module may not depend on it).
 */
public interface RegionSenderNames {

    /** Display name of the region, empty when unknown. */
    Optional<String> regionName(UUID regionId);

    /** Mail variable carrying the region name of the sender (template footer, From display name). */
    String MAIL_VARIABLE = "senderRegion";

    /** Region name of a sender region through the (optional) directory; empty without region or directory. */
    static Optional<String> resolve(ObjectProvider<RegionSenderNames> names, UUID regionId) {
        RegionSenderNames directory = regionId == null ? null : names.getIfAvailable();
        return directory == null ? Optional.empty() : directory.regionName(regionId);
    }

    /** From display name of a mail sent on behalf of a region. */
    static String fromName(String regionName) {
        return "foodsharing " + regionName;
    }

    /** Last line of a Telegram message sent on behalf of a region. */
    static String signature(String regionName) {
        return "— " + fromName(regionName);
    }

    /** {@code text} with the signature line of the region (unchanged without a region name). */
    static String signed(String text, String regionName) {
        if (regionName == null || regionName.isBlank()) {
            return text;
        }
        return (text == null || text.isBlank() ? "" : text + "\n\n") + signature(regionName);
    }
}
