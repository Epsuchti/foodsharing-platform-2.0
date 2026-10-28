package network.foodsharing.basket.service;

import java.time.Instant;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketLocationType;
import network.foodsharing.basket.domain.BasketRequestStatus;
import network.foodsharing.basket.domain.BasketStatus;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.user.service.PhoneNumberValidator;

/** Pure basket rules (upstream {@code BasketPermissions}, {@code BasketTransactions}). */
public final class BasketRules {

    public static final int DEFAULT_LIFETIME_DAYS = 7;
    public static final int MAX_LIFETIME_DAYS = 21;
    /** Decimal places of HOME coordinates shown to others (about 100 m). */
    static final int HOME_PRECISION = 3;

    private BasketRules() {
    }

    /**
     * Validates a complete basket state and normalises the phone numbers.
     *
     * @throws ApiException 400 VALIDATION_FAILED, BASKET_LOCATION_REQUIRED, BASKET_CONTACT_REQUIRED,
     *                      INVALID_PHONE_NUMBER
     */
    public static void validate(Basket basket) {
        if (basket.getDescription() == null || basket.getDescription().isBlank()
                || basket.getDescription().length() > 4000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "description");
        }
        if (basket.getLifetimeDays() < 1 || basket.getLifetimeDays() > MAX_LIFETIME_DAYS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "lifetimeDays");
        }
        if (basket.getWeightGrams() != null && (basket.getWeightGrams() < 0 || basket.getWeightGrams() > 100000)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "weightGrams");
        }
        if (!validCoordinates(basket.getLat(), basket.getLon())) {
            throw ApiException.badRequest(ApiErrorCode.BASKET_LOCATION_REQUIRED);
        }
        if (!basket.isContactMessage() && !basket.isContactPhone() && !basket.isContactMobile()) {
            throw ApiException.badRequest(ApiErrorCode.BASKET_CONTACT_REQUIRED);
        }
        basket.setPhone(phone(basket.getPhone(), basket.isContactPhone(), "phone"));
        basket.setMobile(phone(basket.getMobile(), basket.isContactMobile(), "mobile"));
    }

    static boolean validCoordinates(Double lat, Double lon) {
        return lat != null && lon != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
                && !(lat == 0 && lon == 0);
    }

    private static String phone(String value, boolean required, String field) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw ApiException.badRequest(ApiErrorCode.INVALID_PHONE_NUMBER).withArg("field", field);
            }
            return null;
        }
        if (!PhoneNumberValidator.isValid(value)) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_PHONE_NUMBER).withArg("field", field);
        }
        return PhoneNumberValidator.normalize(value);
    }

    /** ACTIVE and not yet expired. */
    public static boolean isOpen(Basket basket, Instant now) {
        return basket.getStatus() == BasketStatus.ACTIVE && basket.getExpiresAt().isAfter(now);
    }

    /** Owner and ORGA may delete; only the owner edits. */
    public static boolean mayDelete(Basket basket, java.util.UUID userId, boolean orga) {
        return orga || basket.getOwnerId().equals(userId);
    }

    public static boolean mayRequest(Basket basket, java.util.UUID userId, Instant now) {
        return !basket.getOwnerId().equals(userId) && isOpen(basket, now);
    }

    /** A request still counts (shown to the owner, blocks a second one). */
    public static boolean isLive(BasketRequestStatus status) {
        return status != BasketRequestStatus.WITHDRAWN;
    }

    /** HOME coordinates are rounded for everybody but the owner (privacy). */
    public static double publicCoordinate(Basket basket, double value, boolean owner) {
        if (owner || basket.getLocationType() != BasketLocationType.HOME) {
            return value;
        }
        double factor = Math.pow(10, HOME_PRECISION);
        return Math.round(value * factor) / factor;
    }

    /** Great-circle distance in km. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
