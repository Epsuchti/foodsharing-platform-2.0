package network.foodsharing.region.web;

import java.util.List;
import network.foodsharing.api.PublicRegionsApi;
import network.foodsharing.api.model.PublicRegion;
import network.foodsharing.api.model.PublicRegionNode;
import network.foodsharing.api.model.RegionSuggestion;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.PublicRegionService;
import network.foodsharing.region.service.RegionSuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public region API (plan §13.2 R4, §8.3 region suggestion, §16 region tree). */
@RestController
public class PublicRegionsController implements PublicRegionsApi {

    private final PublicRegionService publicRegions;
    private final RegionSuggestionService suggestions;
    private final RegionMapper mapper;

    public PublicRegionsController(PublicRegionService publicRegions, RegionSuggestionService suggestions,
                                   RegionMapper mapper) {
        this.publicRegions = publicRegions;
        this.suggestions = suggestions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PublicRegion> getPublicRegion(String slug) {
        return ResponseEntity.ok(mapper.toPublic(publicRegions.region(slug)));
    }

    @Override
    public ResponseEntity<List<PublicRegionNode>> getPublicRegionTree(String rootSlug) {
        return ResponseEntity.ok(publicRegions.tree(rootSlug).stream().map(mapper::toNode).toList());
    }

    @Override
    public ResponseEntity<List<RegionSuggestion>> suggestRegions(String postcode, Double lat, Double lon,
                                                                 String country) {
        return ResponseEntity.ok(suggestions.suggest(postcode, lat, lon, country).stream()
                .map(mapper::toSuggestion).toList());
    }
}
