package network.foodsharing.content.web;

import network.foodsharing.api.model.BlogPost;
import network.foodsharing.api.model.BlogPostPage;
import network.foodsharing.api.model.DonationPage;
import network.foodsharing.api.model.FaqEntry;
import network.foodsharing.api.model.Petition;
import network.foodsharing.api.model.PublicStatistics;
import network.foodsharing.api.model.ReleaseNote;
import network.foodsharing.api.model.TeamGroup;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import java.util.List;
import network.foodsharing.api.PublicContentApi;
import network.foodsharing.api.model.ContentPage;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.LearningChapter;
import network.foodsharing.content.service.ContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Published content pages and learning chapters (no login). */
@RestController
public class PublicContentController implements PublicContentApi {

    private final ContentService content;

    public PublicContentController(ContentService content) {
        this.content = content;
    }

    @Override
    public ResponseEntity<ContentPage> getContentPage(String slug, Language lang) {
        return ResponseEntity.ok(ContentModels.page(content.page(slug, ContentModels.language(lang))));
    }

    @Override
    public ResponseEntity<ContentPage> getLearningChapter(String key, Language lang) {
        return ResponseEntity.ok(ContentModels.page(content.learningChapter(key, ContentModels.language(lang))));
    }

    @Override
    public ResponseEntity<List<LearningChapter>> listLearningChapters(Language lang) {
        return ResponseEntity.ok(content.learningChapters(ContentModels.language(lang)).stream()
                .map(ContentModels::chapter).toList());
    }

    // --- M6 contract stubs (docs/plan-m6.md B1) ---

    @Override
    public ResponseEntity<DonationPage> getDonationPage() {
        throw ApiException.notImplemented("getDonationPage");
    }

    @Override
    public ResponseEntity<Petition> getPetition() {
        throw ApiException.notImplemented("getPetition");
    }

    @Override
    public ResponseEntity<BlogPost> getPublicBlogPost(UUID blogPostId) {
        throw ApiException.notImplemented("getPublicBlogPost");
    }

    @Override
    public ResponseEntity<PublicStatistics> getPublicStatistics() {
        throw ApiException.notImplemented("getPublicStatistics");
    }

    @Override
    public ResponseEntity<List<TeamGroup>> getPublicTeam() {
        throw ApiException.notImplemented("getPublicTeam");
    }

    @Override
    public ResponseEntity<List<FaqEntry>> listFaq(Language lang) {
        throw ApiException.notImplemented("listFaq");
    }

    @Override
    public ResponseEntity<BlogPostPage> listPublicBlogPosts(UUID regionId, Integer page, Integer size) {
        throw ApiException.notImplemented("listPublicBlogPosts");
    }

    @Override
    public ResponseEntity<List<ReleaseNote>> listReleaseNotes(Language lang) {
        throw ApiException.notImplemented("listReleaseNotes");
    }
}
