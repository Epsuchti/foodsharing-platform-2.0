package network.foodsharing.region.service;

import java.util.Optional;

/**
 * Postcode → coordinates (plan §8.3 region suggestion). {@link PhotonGeocodingClient} in production
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

    /** The provider is down; callers answer 503 GEOCODING_UNAVAILABLE. */
    class GeocodingUnavailableException extends RuntimeException {
        public GeocodingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
