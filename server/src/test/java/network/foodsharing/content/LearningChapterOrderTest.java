package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.content.domain.ContentPage;
import network.foodsharing.content.domain.ContentPageI18n;
import network.foodsharing.content.domain.ContentPageKind;
import network.foodsharing.content.domain.LearningChapterGroup;
import network.foodsharing.content.repository.ContentPageI18nRepository;
import network.foodsharing.content.repository.ContentPageRepository;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** C1: chapter groups and the required flag; reading order is group, then sort order. */
class LearningChapterOrderTest {

    private final ContentPageRepository pages = mock(ContentPageRepository.class);
    private final ContentPageI18nRepository texts = mock(ContentPageI18nRepository.class);
    private ContentService service;

    @BeforeEach
    void setUp() {
        service = new ContentService(pages, texts, mock(RegionService.class), mock(CurrentActorService.class),
                mock(PermissionService.class), mock(HistoryService.class), mock(UploadService.class));
        // sorted by sort order only (as the repository does): the MORE chapter comes before BASICS ones
        when(pages.findByKindOrderBySortOrderAscSlugAscIdAsc(ContentPageKind.LEARNING_CHAPTER)).thenReturn(List.of(
                chapter("principles", 10, null, true, true),
                chapter("rule-violations-explained", 15, LearningChapterGroup.MORE, false, true),
                chapter("code-of-conduct", 20, LearningChapterGroup.BASICS, true, true),
                chapter("draft", 25, LearningChapterGroup.BASICS, true, false),
                chapter("store-basics", 5, LearningChapterGroup.STORE_MANAGER, true, true)));
        when(texts.findByOwnerId(any())).thenAnswer(call -> {
            ContentPageI18n text = new ContentPageI18n();
            text.setOwnerId(call.getArgument(0));
            text.setLanguage("de");
            text.setTitle("T");
            text.setBody("B");
            return List.of(text);
        });
    }

    private static ContentPage chapter(String slug, int sort, LearningChapterGroup group, boolean required,
                                       boolean published) {
        ContentPage page = new ContentPage();
        page.setId(UUID.randomUUID());
        page.setSlug(slug);
        page.setKind(ContentPageKind.LEARNING_CHAPTER);
        page.setSortOrder(sort);
        page.setChapterGroup(group);
        page.setRequired(required);
        page.setPublished(published);
        return page;
    }

    @Test
    void readingOrderFollowsTheGroupsThenTheSortOrder() {
        assertThat(service.learningChapterKeys()).containsExactly("principles", "code-of-conduct",
                "rule-violations-explained", "store-basics");
    }

    @Test
    void onlyPublishedRequiredChaptersAreRequired() {
        assertThat(service.requiredChapterKeys()).containsExactly("principles", "code-of-conduct", "store-basics");
    }

    @Test
    void nextChapterCrossesGroups() {
        assertThat(service.learningChapter("code-of-conduct", "de").nextChapterKey())
                .isEqualTo("rule-violations-explained");
        assertThat(service.learningChapter("rule-violations-explained", "de").nextChapterKey())
                .isEqualTo("store-basics");
        assertThat(service.learningChapter("store-basics", "de").nextChapterKey()).isNull();
    }

    @Test
    void chaptersWithoutGroupCountAsBasics() {
        assertThat(ContentService.groupOf(chapter("x", 1, null, true, true))).isEqualTo(LearningChapterGroup.BASICS);
    }
}
