package network.foodsharing.content.web;

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
}
