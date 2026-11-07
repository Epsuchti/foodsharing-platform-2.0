package network.foodsharing.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.GeocodingClient.GeoPoint;
import network.foodsharing.region.service.RegionReadService;
import org.junit.jupiter.api.Test;

/** Decision 171 (D2): address lookup for the store and Fairteiler forms. */
class MapSearchServiceTest {

    private final GeocodingClient geocoding = mock(GeocodingClient.class);
    private final MapSearchService service = new MapSearchService(geocoding, mock(RegionReadService.class));

    @Test
    void geocodesTheStrippedAddressWithALabel() {
        when(geocoding.geocodeAddress("CH", "Bahnhofstrasse 1", "8001", "Zürich"))
                .thenReturn(Optional.of(new GeoPoint(47.3769, 8.5402)));

        assertThat(service.geocodeAddress(" Bahnhofstrasse 1 ", " 8001", "Zürich ", null))
                .contains(new MapSearchService.GeoHit("Bahnhofstrasse 1, 8001 Zürich", 47.3769, 8.5402));
    }

    @Test
    void blankStreetAndCityAreLeftOutAndTheCountryIsUpperCase() {
        when(geocoding.geocodeAddress("DE", null, "10115", null)).thenReturn(Optional.of(new GeoPoint(52.53, 13.38)));

        assertThat(service.geocodeAddress("", "10115", " ", "de"))
                .contains(new MapSearchService.GeoHit("10115", 52.53, 13.38));
    }

    @Test
    void unknownIsEmpty() {
        when(geocoding.geocodeAddress(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThat(service.geocodeAddress("Nirgendweg 1", "9999", "Nirgendwo", "CH")).isEmpty();
    }

    @Test
    void invalidInputIsRejected() {
        for (String[] in : new String[][] {{null, null, null, null}, {null, " ", null, null},
                {null, "8001", null, "CHE"}, {"x".repeat(201), "8001", null, null},
                {null, "8001", "x".repeat(201), null}, {null, "1".repeat(21), null, null}}) {
            assertThatThrownBy(() -> service.geocodeAddress(in[0], in[1], in[2], in[3]))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
    }

    @Test
    void anUnavailableGeocoderIs503() {
        when(geocoding.geocodeAddress(any(), any(), any(), any()))
                .thenThrow(new GeocodingClient.GeocodingUnavailableException("down", null));

        assertThatThrownBy(() -> service.geocodeAddress(null, "8001", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.GEOCODING_UNAVAILABLE);
                    assertThat(e.getStatus().value()).isEqualTo(503);
                });
    }
}
