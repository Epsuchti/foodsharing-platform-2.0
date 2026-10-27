package network.foodsharing.content.web;

import java.util.List;
import network.foodsharing.api.ContentApi;
import network.foodsharing.api.model.ContentPageKind;
import network.foodsharing.api.model.ContentPageSummary;
import network.foodsharing.api.model.ContentPageUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content administration API (implemented in T14).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class ContentController implements ContentApi {

    @Override
    public ResponseEntity<ContentPageUpdate> getContentPageForEdit(String slug) {
        throw ApiException.notImplemented("getContentPageForEdit");
    }

    @Override
    public ResponseEntity<List<ContentPageSummary>> listContentPages(ContentPageKind kind) {
        throw ApiException.notImplemented("listContentPages");
    }

    @Override
    public ResponseEntity<ContentPageSummary> upsertContentPage(String slug, ContentPageUpdate contentPageUpdate) {
        throw ApiException.notImplemented("upsertContentPage");
    }
}
