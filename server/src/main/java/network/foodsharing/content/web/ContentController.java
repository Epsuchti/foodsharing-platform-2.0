package network.foodsharing.content.web;

import java.util.List;
import network.foodsharing.api.ContentApi;
import network.foodsharing.api.model.ContentPageKind;
import network.foodsharing.api.model.ContentPageSummary;
import network.foodsharing.api.model.ContentPageUpdate;
import network.foodsharing.content.service.ContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Content page administration (ORGA / PR function group admins). */
@RestController
public class ContentController implements ContentApi {

    private final ContentService content;

    public ContentController(ContentService content) {
        this.content = content;
    }

    @Override
    public ResponseEntity<ContentPageUpdate> getContentPageForEdit(String slug) {
        return ResponseEntity.ok(ContentModels.edit(content.pageForEdit(slug)));
    }

    @Override
    public ResponseEntity<List<ContentPageSummary>> listContentPages(ContentPageKind kind) {
        return ResponseEntity.ok(content.allPages(kind == null ? null
                        : network.foodsharing.content.domain.ContentPageKind.valueOf(kind.name())).stream()
                .map(ContentModels::summary).toList());
    }

    @Override
    public ResponseEntity<ContentPageSummary> upsertContentPage(String slug, ContentPageUpdate body) {
        return ResponseEntity.ok(ContentModels.summary(content.upsert(slug, new ContentService.PageInput(
                body.getTitles(), body.getBodies(), network.foodsharing.content.domain.ContentPageKind.valueOf(
                        body.getKind().name()), body.getSortOrder(), body.getEstimatedMinutes(),
                Boolean.TRUE.equals(body.getPublished()), body.getReason()))));
    }
}
