package network.foodsharing.store.service;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.store.domain.PublicityStatus;

/**
 * The {@code publicly_named} flag of a store (decision 153, S17 D6). ORGA and this store's managers switch it, on
 * only with {@code publicity = ALLOWED}. Moving {@code publicity} off ALLOWED clears it, whoever does that.
 */
public final class PublicNamingRules {

    private PublicNamingRules() {
    }

    public static boolean mayChange(boolean orga, boolean manager) {
        return orga || manager;
    }

    /**
     * The flag's value after an update.
     *
     * @param current   the flag now
     * @param requested the requested value, null for unchanged
     * @param publicity {@code publicity} after the update
     * @param mayChange {@link #mayChange}; applies only when the request changes the flag
     * @throws ApiException 403 ACCESS_DENIED, 409 PUBLICITY_NOT_ALLOWED
     */
    public static boolean resolve(boolean current, Boolean requested, PublicityStatus publicity, boolean mayChange) {
        boolean wanted = requested == null ? current : requested;
        if (wanted != current && !mayChange) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (publicity == PublicityStatus.ALLOWED) {
            return wanted;
        }
        if (wanted && !current) {
            throw ApiException.conflict(ApiErrorCode.PUBLICITY_NOT_ALLOWED);
        }
        return false;
    }
}
