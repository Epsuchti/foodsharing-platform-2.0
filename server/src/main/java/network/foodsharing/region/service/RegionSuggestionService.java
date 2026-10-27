package network.foodsharing.region.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionPostcode;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionPostcodeRepository;
import network.foodsharing.region.repository.RegionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region suggestions for onboarding step 2 (plan §8.3, §16): regions listed for the postcode first; otherwise the
 * nearest active accessible regions with ambassadors ("any region can be activated when it has ambassadors") within
 * {@code app.region.suggestion-radius-km} of the given position or the geocoded postcode.
 */
@Service
@Transactional(readOnly = true)
public class RegionSuggestionService {

    public enum MatchedBy { POSTCODE, DISTANCE }

    public record Suggestion(Region region, Double distanceKm, MatchedBy matchedBy, int ambassadorCount) {
    }

    static final int MAX_SUGGESTIONS = 5;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final RegionPostcodeRepository postcodes;
    private final RegionRepository regions;
    private final RegionAdminRepository admins;
    private final GeocodingClient geocoding;
    private final AppProperties properties;

    public RegionSuggestionService(RegionPostcodeRepository postcodes, RegionRepository regions,
                                   RegionAdminRepository admins, GeocodingClient geocoding, AppProperties properties) {
        this.postcodes = postcodes;
        this.regions = regions;
        this.admins = admins;
        this.geocoding = geocoding;
        this.properties = properties;
    }

    /**
     * @throws ApiException 400 VALIDATION_FAILED without postcode and position, 503 GEOCODING_UNAVAILABLE
     */
    public List<Suggestion> suggest(String postcode, Double latitude, Double longitude, String country) {
        String countryCode = country == null || country.isBlank() ? "CH" : country.strip().toUpperCase(Locale.ROOT);
        boolean hasPostcode = postcode != null && !postcode.isBlank();
        if (!hasPostcode && (latitude == null || longitude == null)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (hasPostcode) {
            List<RegionPostcode> matches = postcodes.findByCountryAndPostcode(countryCode, postcode.strip());
            if (!matches.isEmpty()) {
                return regions.findByIdIn(matches.stream().map(RegionPostcode::getRegionId).toList()).stream()
                        .filter(Region::isActive)
                        .map(region -> new Suggestion(region, null, MatchedBy.POSTCODE, ambassadorCount(region)))
                        .sorted(Comparator.comparing((Suggestion s) -> s.region().getName()))
                        .toList();
            }
        }
        Optional<GeocodingClient.GeoPoint> point;
        if (latitude != null && longitude != null) {
            point = Optional.of(new GeocodingClient.GeoPoint(latitude, longitude));
        } else {
            try {
                point = geocoding.geocodePostcode(countryCode, postcode.strip());
            } catch (GeocodingClient.GeocodingUnavailableException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GEOCODING_UNAVAILABLE);
            }
        }
        return point.map(this::nearest).orElse(List.of());
    }

    private List<Suggestion> nearest(GeocodingClient.GeoPoint point) {
        double radius = properties.getRegion().getSuggestionRadiusKm();
        Map<UUID, Suggestion> result = new LinkedHashMap<>();
        for (Region region : regions.findAll()) {
            if (!region.isActive() || region.getType() == null || !region.getType().isAccessible()
                    || region.getLatitude() == null || region.getLongitude() == null) {
                continue;
            }
            int ambassadors = ambassadorCount(region);
            if (ambassadors == 0) {
                continue;
            }
            double distance = distanceKm(point.latitude(), point.longitude(), region.getLatitude(), region.getLongitude());
            if (distance <= radius) {
                result.put(region.getId(), new Suggestion(region, Math.round(distance * 10) / 10.0, MatchedBy.DISTANCE,
                        ambassadors));
            }
        }
        return result.values().stream().sorted(Comparator.comparing(Suggestion::distanceKm))
                .limit(MAX_SUGGESTIONS).toList();
    }

    private int ambassadorCount(Region region) {
        return admins.findUserIds(region.getId()).size();
    }

    /** Great-circle distance (haversine). */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
