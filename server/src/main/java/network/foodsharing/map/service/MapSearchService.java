package network.foodsharing.map.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionReadService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Map location search (M1): Swiss postcodes through the geocoder plus regions with coordinates by name; address
 * lookup for the store and Fairteiler forms (decision 171).
 */
@Service
@Transactional(readOnly = true)
public class MapSearchService {

    /** A place to centre the map on. */
    public record GeoHit(String label, double lat, double lon) {
    }

    private final GeocodingClient geocoding;
    private final RegionReadService regions;

    public MapSearchService(GeocodingClient geocoding, RegionReadService regions) {
        this.geocoding = geocoding;
        this.regions = regions;
    }

    /** @throws ApiException 400 VALIDATION_FAILED (blank / too long), 503 INTEGRATION_UNAVAILABLE (postcode) */
    public List<GeoHit> search(String q) {
        if (q == null || q.isBlank() || q.length() > 100) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "q");
        }
        String query = q.strip();
        List<GeoHit> hits = new ArrayList<>();
        if (query.matches("\\d{4,5}")) {
            try {
                geocoding.geocodePostcode("CH", query)
                        .ifPresent(p -> hits.add(new GeoHit(query, p.latitude(), p.longitude())));
            } catch (GeocodingClient.GeocodingUnavailableException e) {
                throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        ApiErrorCode.INTEGRATION_UNAVAILABLE);
            }
        }
        regions.search(query, List.of(UnitType.values()), 0, 20).getContent().stream()
                .filter(r -> !r.getType().isGroup() && r.getLatitude() != null && r.getLongitude() != null)
                .limit(10).forEach(r -> hits.add(new GeoHit(r.getName(), r.getLatitude(), r.getLongitude())));
        return hits;
    }

    /**
     * The pin position for a postal address: Photon address search, postcode fallback.
     *
     * @return empty when neither the address nor the postcode is known
     * @throws ApiException 400 VALIDATION_FAILED (postcode missing, country not two letters, field too long),
     *                      503 GEOCODING_UNAVAILABLE
     */
    public Optional<GeoHit> geocodeAddress(String street, String postcode, String city, String country) {
        if (postcode == null || postcode.isBlank() || postcode.length() > 20) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "postcode");
        }
        if (street != null && street.length() > 200) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "street");
        }
        if (city != null && city.length() > 200) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "city");
        }
        String countryCode = country == null || country.isBlank() ? "CH" : country.strip().toUpperCase(Locale.ROOT);
        if (!countryCode.matches("[A-Z]{2}")) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "country");
        }
        String label = label(street, postcode, city);
        try {
            return geocoding.geocodeAddress(countryCode, blankToNull(street), postcode.strip(), blankToNull(city))
                    .map(p -> new GeoHit(label, p.latitude(), p.longitude()));
        } catch (GeocodingClient.GeocodingUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GEOCODING_UNAVAILABLE);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String label(String street, String postcode, String city) {
        String place = (postcode.strip() + " " + (city == null ? "" : city.strip())).strip();
        return street == null || street.isBlank() ? place : street.strip() + ", " + place;
    }
}
