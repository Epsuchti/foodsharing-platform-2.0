package network.foodsharing.content.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.ContentApi;
import network.foodsharing.api.model.BlogPost;
import network.foodsharing.api.model.BlogPostCreate;
import network.foodsharing.api.model.BlogPostPage;
import network.foodsharing.api.model.BlogPostUpdate;
import network.foodsharing.api.model.ContentPage;
import network.foodsharing.api.model.ContentPageCreate;
import network.foodsharing.api.model.ContentPageKind;
import network.foodsharing.api.model.ContentPageSummary;
import network.foodsharing.api.model.ContentPageUpdate;
import network.foodsharing.api.model.PublishedUpdate;
import network.foodsharing.api.model.ReleaseNote;
import network.foodsharing.api.model.ReleaseNoteCreate;
import network.foodsharing.api.model.ReleaseNoteTranslation;
import network.foodsharing.api.model.ReleaseNoteUpdate;
import network.foodsharing.content.mapper.ContentMapper;
import network.foodsharing.content.service.BlogService;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.ReleaseNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Content pages, blog and release notes administration (B11). */
@RestController
public class ContentController implements ContentApi {

    private final ContentService content;
    private final BlogService blog;
    private final ReleaseNoteService releaseNotes;
    private final ContentMapper mapper;

    public ContentController(ContentService content, BlogService blog, ReleaseNoteService releaseNotes,
                             ContentMapper mapper) {
        this.content = content;
        this.blog = blog;
        this.releaseNotes = releaseNotes;
        this.mapper = mapper;
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
                Boolean.TRUE.equals(body.getPublished()), body.getReason(), body.getEditorFunction(),
                body.getImageUploadIds(), body.getRemoveImageUploadIds()))));
    }

    @Override
    public ResponseEntity<ContentPage> createContentPage(ContentPageCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ContentMapper.page(content.create(body.getSlug(),
                network.foodsharing.content.domain.ContentPageKind.valueOf(body.getKind().getValue()), "de",
                body.getTitle(), body.getBody(), body.getEditorFunction(), body.getImageUploadIds())));
    }

    // ------------------------------------------------------------------------------------------- blog

    @Override
    public ResponseEntity<BlogPostPage> listBlogPosts(Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toEditPage(blog.editable(page == null ? 0 : page, size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<BlogPost> getBlogPost(UUID blogPostId) {
        return ResponseEntity.ok(mapper.toPost(blog.get(blogPostId)));
    }

    @Override
    public ResponseEntity<BlogPost> createBlogPost(BlogPostCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toPost(blog.create(new BlogService.PostInput(
                body.getTitle(), body.getTeaser(), body.getBody(), body.getRegionId(), body.getPictureUploadId()))));
    }

    @Override
    public ResponseEntity<BlogPost> updateBlogPost(UUID blogPostId, BlogPostUpdate body) {
        return ResponseEntity.ok(mapper.toPost(blog.update(blogPostId, new BlogService.PostInput(body.getTitle(),
                body.getTeaser(), body.getBody(), body.getRegionId(), body.getPictureUploadId(),
                Boolean.TRUE.equals(body.getRemovePicture())))));
    }

    @Override
    public ResponseEntity<BlogPost> setBlogPostPublished(UUID blogPostId, PublishedUpdate body) {
        return ResponseEntity.ok(mapper.toPost(blog.setPublished(blogPostId, Boolean.TRUE.equals(body.getPublished()))));
    }

    @Override
    public ResponseEntity<Void> deleteBlogPost(UUID blogPostId) {
        blog.delete(blogPostId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------------------------- release notes

    @Override
    public ResponseEntity<List<ReleaseNote>> listReleaseNotesForEdit() {
        return ResponseEntity.ok(releaseNotes.listForEdit().stream().map(ContentMapper::toReleaseNote).toList());
    }

    @Override
    public ResponseEntity<ReleaseNote> createReleaseNote(ReleaseNoteCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ContentMapper.toReleaseNote(releaseNotes.create(
                new ReleaseNoteService.NoteInput(body.getVersion(), body.getReleasedAt(), body.getTitle(),
                        body.getBody(), translations(body.getTranslations())))));
    }

    @Override
    public ResponseEntity<ReleaseNote> updateReleaseNote(UUID releaseNoteId, ReleaseNoteUpdate body) {
        return ResponseEntity.ok(ContentMapper.toReleaseNote(releaseNotes.update(releaseNoteId,
                new ReleaseNoteService.NoteInput(body.getVersion(), body.getReleasedAt(), body.getTitle(),
                        body.getBody(), translations(body.getTranslations()),
                        body.getRemoveTranslations() == null ? null
                                : new java.util.HashSet<>(body.getRemoveTranslations())))));
    }

    @Override
    public ResponseEntity<Void> deleteReleaseNote(UUID releaseNoteId) {
        releaseNotes.delete(releaseNoteId);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, ReleaseNoteService.Text> translations(Map<String, ReleaseNoteTranslation> values) {
        if (values == null) {
            return null;
        }
        Map<String, ReleaseNoteService.Text> result = new HashMap<>();
        values.forEach((language, text) -> result.put(language, new ReleaseNoteService.Text(text.getTitle(),
                text.getBody())));
        return result;
    }
}
