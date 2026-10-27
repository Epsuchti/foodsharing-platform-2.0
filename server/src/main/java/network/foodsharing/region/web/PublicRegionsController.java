package network.foodsharing.region.web;

import java.util.List;
import network.foodsharing.api.PublicRegionsApi;
import network.foodsharing.api.model.PublicRegion;
import network.foodsharing.api.model.PublicRegionNode;
import network.foodsharing.api.model.RegionSuggestion;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public region API (implemented in T12).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class PublicRegionsController implements PublicRegionsApi {

    @Override
    public ResponseEntity<PublicRegion> getPublicRegion(String slug) {
        throw ApiException.notImplemented("getPublicRegion");
    }

    @Override
    public ResponseEntity<List<PublicRegionNode>> getPublicRegionTree(String rootSlug) {
        throw ApiException.notImplemented("getPublicRegionTree");
    }

    @Override
    public ResponseEntity<List<RegionSuggestion>> suggestRegions(String postcode, Double lat, Double lon, String country) {
        throw ApiException.notImplemented("suggestRegions");
    }
}
