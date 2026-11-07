package network.foodsharing.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.map.service.BubbleService;
import network.foodsharing.map.service.MapSearchService;
import network.foodsharing.map.service.MarkerService;
import network.foodsharing.map.web.MapController;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.WithSeedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Decision 171 (D2): every answer documented for geocodeAddress in paths/map.yaml. */
@WebMvcTest(MapController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class MapControllerTest {

    static final String URL = "/api/map/geocode-address";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarkerService markers;

    @MockitoBean
    private BubbleService bubbles;

    @MockitoBean
    private MapSearchService search;

    static ResultActions expect(ResultActions actions, int status, ApiErrorCode code) throws Exception {
        return actions.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code.getValue()));
    }

    @Test
    void anonymousCallsNeedAuthentication() throws Exception {
        expect(mvc.perform(get(URL).param("postcode", "8001")), 401, ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    @WithSeedUser("foodsaver")
    void foundAddressIsAGeoResult() throws Exception {
        when(search.geocodeAddress("Bahnhofstrasse 1", "8001", "Zürich", "CH"))
                .thenReturn(Optional.of(new MapSearchService.GeoHit("Bahnhofstrasse 1, 8001 Zürich", 47.3769, 8.5402)));

        mvc.perform(get(URL).param("street", "Bahnhofstrasse 1").param("postcode", "8001").param("city", "Zürich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Bahnhofstrasse 1, 8001 Zürich"))
                .andExpect(jsonPath("$.lat").value(47.3769))
                .andExpect(jsonPath("$.lon").value(8.5402));
    }

    @Test
    @WithSeedUser("foodsaver")
    void unknownAddressIsNoContent() throws Exception {
        when(search.geocodeAddress(any(), any(), any(), any())).thenReturn(Optional.empty());

        mvc.perform(get(URL).param("postcode", "9999")).andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @WithSeedUser("foodsaver")
    void errors() throws Exception {
        expect(mvc.perform(get(URL)), 400, ApiErrorCode.VALIDATION_FAILED);
        expect(mvc.perform(get(URL).param("postcode", "8001").param("country", "CHE")), 400,
                ApiErrorCode.VALIDATION_FAILED);
        when(search.geocodeAddress(any(), any(), any(), any()))
                .thenThrow(ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED));
        expect(mvc.perform(get(URL).param("postcode", " ")), 400, ApiErrorCode.VALIDATION_FAILED);
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GEOCODING_UNAVAILABLE)).when(search)
                .geocodeAddress(any(), any(), any(), any());
        expect(mvc.perform(get(URL).param("postcode", "8001")), 503, ApiErrorCode.GEOCODING_UNAVAILABLE);
    }
}
