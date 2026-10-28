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
import network.foodsharing.content.mapper.ContentMapper;
import network.foodsharing.content.service.BlogService;
import network.foodsharing.content.service.CampaignService;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.ReleaseNoteService;
import network.foodsharing.content.service.TeamPageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Published content pages and learning chapters (no login). */
@RestController
public class PublicContentController implements PublicContentApi {

    private final ContentService content;
    private final BlogService blog;
    private final ReleaseNoteService releaseNotes;
    private final TeamPageService team;
    private final CampaignService campaigns;
    private final ContentMapper mapper;

    public PublicContentController(ContentService content, BlogService blog, ReleaseNoteService releaseNotes,
                                   TeamPageService team, CampaignService campaigns, ContentMapper mapper) {
        this.content = content;
        this.blog = blog;
        this.releaseNotes = releaseNotes;
        this.team = team;
        this.campaigns = campaigns;
        this.mapper = mapper;
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

    // --- M6 (B11) ---

    @Override
    public ResponseEntity<DonationPage> getDonationPage(Language lang) {
        return ResponseEntity.ok(ContentMapper.toDonationPage(campaigns.donation(ContentModels.language(lang))));
    }

    @Override
    public ResponseEntity<Petition> getPetition() {
        return ResponseEntity.ok(ContentMapper.toPetition(campaigns.petition()));
    }

    @Override
    public ResponseEntity<BlogPost> getPublicBlogPost(UUID blogPostId) {
        return ResponseEntity.ok(mapper.toPost(blog.publishedPost(blogPostId)));
    }

    /** B12 (statistics module). */
    @Override
    public ResponseEntity<PublicStatistics> getPublicStatistics() {
        throw ApiException.notImplemented("getPublicStatistics");
    }

    @Override
    public ResponseEntity<List<TeamGroup>> getPublicTeam() {
        return ResponseEntity.ok(team.groups().stream().map(ContentMapper::toTeamGroup).toList());
    }

    @Override
    public ResponseEntity<List<FaqEntry>> listFaq(Language lang) {
        return ResponseEntity.ok(content.faq(ContentModels.language(lang)).stream().map(ContentMapper::toFaq).toList());
    }

    @Override
    public ResponseEntity<BlogPostPage> listPublicBlogPosts(UUID regionId, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toPage(blog.published(regionId, page == null ? 0 : page,
                size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<List<ReleaseNote>> listReleaseNotes(Language lang) {
        return ResponseEntity.ok(releaseNotes.list(ContentModels.language(lang)).stream()
                .map(ContentMapper::toReleaseNote).toList());
    }
}
