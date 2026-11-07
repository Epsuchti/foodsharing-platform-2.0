package network.foodsharing.region.service;

import java.util.Optional;

/**
 * Postcode or address → coordinates (plan §8.3 region suggestion, decision 171 address pins). {@link PhotonGeocodingClient} in production
 * ({@code app.geocoding.provider=photon}), {@link StaticGeocodingClient} offline in local/e2e/test
 * ({@code app.geocoding.provider=static}).
 */
public interface GeocodingClient {

    record GeoPoint(double latitude, double longitude) {
    }

    /**
     * @return empty when the postcode is unknown
     * @throws GeocodingUnavailableException when the provider cannot be reached (circuit open, timeout)
     */
    Optional<GeoPoint> geocodePostcode(String country, String postcode);

    /**
     * Full address (street with house number, postcode, city); falls back to {@link #geocodePostcode} when the
     * address itself is unknown (decision 171).
     *
     * @return empty when neither the address nor the postcode is known
     * @throws GeocodingUnavailableException when the provider cannot be reached (circuit open, timeout)
     */
    Optional<GeoPoint> geocodeAddress(String country, String street, String postcode, String city);

    /** The provider is down; callers answer 503 GEOCODING_UNAVAILABLE. */
    class GeocodingUnavailableException extends RuntimeException {
        public GeocodingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
