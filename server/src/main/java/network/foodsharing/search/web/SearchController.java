package network.foodsharing.search.web;

import java.util.List;
import java.util.Map;
import network.foodsharing.api.SearchApi;
import network.foodsharing.api.model.SearchHit;
import network.foodsharing.api.model.SearchIndexData;
import network.foodsharing.api.model.SearchIndexEntry;
import network.foodsharing.api.model.SearchKind;
import network.foodsharing.api.model.SearchResults;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Global search (C5). */
@RestController
public class SearchController implements SearchApi {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @Override
    public ResponseEntity<SearchIndexData> getSearchIndex() {
        SearchService.Index index = search.index();
        return ResponseEntity.ok(new SearchIndexData().regions(entries(index.regions())).groups(entries(index.groups()))
                .stores(entries(index.stores())).foodSharePoints(entries(index.foodSharePoints()))
                .conversations(entries(index.conversations())));
    }

    @Override
    public ResponseEntity<SearchResults> search(String q, List<SearchKind> kinds) {
        List<network.foodsharing.search.domain.SearchKind> wanted = kinds == null ? List.of() : kinds.stream()
                .map(k -> network.foodsharing.search.domain.SearchKind.valueOf(k.getValue())).toList();
        Map<network.foodsharing.search.domain.SearchKind, List<SearchHitRow>> hits = search.search(q, wanted);
        return ResponseEntity.ok(new SearchResults()
                .users(hits(hits, network.foodsharing.search.domain.SearchKind.USER))
                .regions(hits(hits, network.foodsharing.search.domain.SearchKind.REGION))
                .groups(hits(hits, network.foodsharing.search.domain.SearchKind.GROUP))
                .stores(hits(hits, network.foodsharing.search.domain.SearchKind.STORE))
                .threads(hits(hits, network.foodsharing.search.domain.SearchKind.THREAD))
                .foodSharePoints(hits(hits, network.foodsharing.search.domain.SearchKind.FSP))
                .events(hits(hits, network.foodsharing.search.domain.SearchKind.EVENT))
                .polls(hits(hits, network.foodsharing.search.domain.SearchKind.POLL)));
    }

    private static List<SearchHit> hits(Map<network.foodsharing.search.domain.SearchKind, List<SearchHitRow>> all,
                                        network.foodsharing.search.domain.SearchKind kind) {
        return all.getOrDefault(kind, List.of()).stream().map(h -> new SearchHit().id(h.entityId())
                .kind(SearchKind.fromValue(h.kind().name())).title(h.title()).subtitle(h.subtitle()).link(h.link())
                .imageUrl(h.imageUrl())).toList();
    }

    private static List<SearchIndexEntry> entries(List<SearchHitRow> hits) {
        return hits.stream().map(h -> new SearchIndexEntry().id(h.entityId()).name(h.title()).subtitle(h.subtitle())
                .link(h.link())).toList();
    }
}
