package network.foodsharing.content.web;

import network.foodsharing.api.model.BlogPost;
import network.foodsharing.api.model.BlogPostCreate;
import network.foodsharing.api.model.BlogPostPage;
import network.foodsharing.api.model.BlogPostUpdate;
import network.foodsharing.api.model.ContentPage;
import network.foodsharing.api.model.ContentPageCreate;
import network.foodsharing.api.model.PublishedUpdate;
import network.foodsharing.api.model.ReleaseNote;
import network.foodsharing.api.model.ReleaseNoteCreate;
import network.foodsharing.api.model.ReleaseNoteUpdate;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
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

    // --- M6 contract stubs (docs/plan-m6.md B1) ---

    @Override
    public ResponseEntity<BlogPost> createBlogPost(BlogPostCreate blogPostCreate) {
        throw ApiException.notImplemented("createBlogPost");
    }

    @Override
    public ResponseEntity<ContentPage> createContentPage(ContentPageCreate contentPageCreate) {
        throw ApiException.notImplemented("createContentPage");
    }

    @Override
    public ResponseEntity<ReleaseNote> createReleaseNote(ReleaseNoteCreate releaseNoteCreate) {
        throw ApiException.notImplemented("createReleaseNote");
    }

    @Override
    public ResponseEntity<Void> deleteBlogPost(UUID blogPostId) {
        throw ApiException.notImplemented("deleteBlogPost");
    }

    @Override
    public ResponseEntity<Void> deleteReleaseNote(UUID releaseNoteId) {
        throw ApiException.notImplemented("deleteReleaseNote");
    }

    @Override
    public ResponseEntity<BlogPost> getBlogPost(UUID blogPostId) {
        throw ApiException.notImplemented("getBlogPost");
    }

    @Override
    public ResponseEntity<BlogPostPage> listBlogPosts(Integer page, Integer size) {
        throw ApiException.notImplemented("listBlogPosts");
    }

    @Override
    public ResponseEntity<List<ReleaseNote>> listReleaseNotesForEdit() {
        throw ApiException.notImplemented("listReleaseNotesForEdit");
    }

    @Override
    public ResponseEntity<BlogPost> setBlogPostPublished(UUID blogPostId, PublishedUpdate publishedUpdate) {
        throw ApiException.notImplemented("setBlogPostPublished");
    }

    @Override
    public ResponseEntity<BlogPost> updateBlogPost(UUID blogPostId, BlogPostUpdate blogPostUpdate) {
        throw ApiException.notImplemented("updateBlogPost");
    }

    @Override
    public ResponseEntity<ReleaseNote> updateReleaseNote(UUID releaseNoteId, ReleaseNoteUpdate releaseNoteUpdate) {
        throw ApiException.notImplemented("updateReleaseNote");
    }
}
