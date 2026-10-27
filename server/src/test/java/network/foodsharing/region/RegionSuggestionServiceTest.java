package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionPostcode;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionPostcodeRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionSuggestionService;
import network.foodsharing.region.service.RegionSuggestionService.MatchedBy;
import network.foodsharing.region.service.RegionSuggestionService.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Postcode table first, then the nearest active regions with ambassadors within 50 km. */
class RegionSuggestionServiceTest {

    private final RegionPostcodeRepository postcodes = mock(RegionPostcodeRepository.class);
    private final RegionRepository regions = mock(RegionRepository.class);
    private final RegionAdminRepository admins = mock(RegionAdminRepository.class);
    private final GeocodingClient geocoding = mock(GeocodingClient.class);
    private RegionSuggestionService service;

    private final Region zuerich = region("zuerich-stadt", UnitType.CITY, 47.3769, 8.5417);
    private final Region winterthur = region("winterthur", UnitType.CITY, 47.4999, 8.7262);
    private final Region bern = region("bern", UnitType.CITY, 46.9480, 7.4474);
    private final Region canton = region("kanton-zuerich", UnitType.FEDERAL_STATE, 47.40, 8.60);

    @BeforeEach
    void setUp() {
        service = new RegionSuggestionService(postcodes, regions, admins, geocoding, new AppProperties());
        when(regions.findAll()).thenReturn(List.of(zuerich, winterthur, bern, canton));
        when(admins.findUserIds(any())).thenReturn(Set.of());
        when(admins.findUserIds(zuerich.getId())).thenReturn(Set.of(UUID.randomUUID()));
        when(admins.findUserIds(bern.getId())).thenReturn(Set.of(UUID.randomUUID()));
        when(admins.findUserIds(canton.getId())).thenReturn(Set.of(UUID.randomUUID()));
    }

    @Test
    void postcodeTableWins() {
        RegionPostcode row = new RegionPostcode();
        row.setRegionId(winterthur.getId());
        when(postcodes.findByCountryAndPostcode("CH", "8400")).thenReturn(List.of(row));
        when(regions.findByIdIn(List.of(winterthur.getId()))).thenReturn(List.of(winterthur));

        List<Suggestion> suggestions = service.suggest(" 8400 ", null, null, "ch");

        assertThat(suggestions).singleElement().satisfies(s -> {
            assertThat(s.region()).isSameAs(winterthur);
            assertThat(s.matchedBy()).isEqualTo(MatchedBy.POSTCODE);
            assertThat(s.distanceKm()).isNull();
            assertThat(s.ambassadorCount()).isZero();
        });
        verify(geocoding, never()).geocodePostcode(anyString(), anyString());
    }

    @Test
    void unknownPostcodeFallsBackToTheNearestAccessibleRegionWithAmbassadors() {
        when(postcodes.findByCountryAndPostcode("CH", "8500")).thenReturn(List.of());
        when(geocoding.geocodePostcode("CH", "8500")).thenReturn(Optional.of(new GeocodingClient.GeoPoint(47.5576, 8.8961)));

        List<Suggestion> suggestions = service.suggest("8500", null, null, null);

        // Winterthur is closer but has no ambassadors; the canton is not accessible; Bern is too far
        assertThat(suggestions).singleElement().satisfies(s -> {
            assertThat(s.region()).isSameAs(zuerich);
            assertThat(s.matchedBy()).isEqualTo(MatchedBy.DISTANCE);
            assertThat(s.distanceKm()).isBetween(30.0, 37.0);
        });
    }

    @Test
    void positionWithoutPostcodeSortsByDistance() {
        when(admins.findUserIds(winterthur.getId())).thenReturn(Set.of(UUID.randomUUID()));
        List<Suggestion> suggestions = service.suggest(null, 47.45, 8.65, null);
        assertThat(suggestions).extracting(Suggestion::region).containsExactly(winterthur, zuerich);
        assertThat(suggestions.get(0).distanceKm()).isLessThan(suggestions.get(1).distanceKm());
    }

    @Test
    void nothingNearbyIsAnEmptyList() {
        when(geocoding.geocodePostcode("CH", "1201")).thenReturn(Optional.of(new GeocodingClient.GeoPoint(46.21, 6.143)));
        assertThat(service.suggest("1201", null, null, "CH")).isEmpty();
        when(geocoding.geocodePostcode("CH", "9999")).thenReturn(Optional.empty());
        assertThat(service.suggest("9999", null, null, "CH")).isEmpty();
    }

    @Test
    void geocodingOutageIs503AndMissingInputIs400() {
        when(geocoding.geocodePostcode("CH", "8500"))
                .thenThrow(new GeocodingClient.GeocodingUnavailableException("down", null));
        assertThatThrownBy(() -> service.suggest("8500", null, null, "CH"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.GEOCODING_UNAVAILABLE);
                    assertThat(e.getStatus().value()).isEqualTo(503);
                });
        assertThatThrownBy(() -> service.suggest(" ", 47.0, null, "CH"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    private static Region region(String slug, UnitType type, double lat, double lon) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setSlug(slug);
        region.setName(slug);
        region.setType(type);
        region.setLatitude(lat);
        region.setLongitude(lon);
        return region;
    }
}
