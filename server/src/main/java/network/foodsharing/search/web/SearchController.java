package network.foodsharing.search.web;

import java.util.List;
import network.foodsharing.api.SearchApi;
import network.foodsharing.api.model.SearchIndexData;
import network.foodsharing.api.model.SearchKind;
import network.foodsharing.api.model.SearchResults;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class SearchController implements SearchApi {

    @Override
    public ResponseEntity<SearchIndexData> getSearchIndex() {
        throw ApiException.notImplemented("getSearchIndex");
    }

    @Override
    public ResponseEntity<SearchResults> search(String q, List<SearchKind> kinds) {
        throw ApiException.notImplemented("search");
    }
}
