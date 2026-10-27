package network.foodsharing.content.web;

import java.util.List;
import network.foodsharing.api.PublicContentApi;
import network.foodsharing.api.model.ContentPage;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.LearningChapter;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public content API (implemented in T14).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class PublicContentController implements PublicContentApi {

    @Override
    public ResponseEntity<ContentPage> getContentPage(String slug, Language lang) {
        throw ApiException.notImplemented("getContentPage");
    }

    @Override
    public ResponseEntity<ContentPage> getLearningChapter(String key, Language lang) {
        throw ApiException.notImplemented("getLearningChapter");
    }

    @Override
    public ResponseEntity<List<LearningChapter>> listLearningChapters(Language lang) {
        throw ApiException.notImplemented("listLearningChapters");
    }
}
