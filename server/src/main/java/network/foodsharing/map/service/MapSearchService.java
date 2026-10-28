package network.foodsharing.map.service;

import java.util.ArrayList;
import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionReadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Map location search (M1): Swiss postcodes through the geocoder plus regions with coordinates by name. */
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
}
