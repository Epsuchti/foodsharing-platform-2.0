package network.foodsharing.content.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.ContentAction;
import network.foodsharing.content.domain.ContentPage;
import network.foodsharing.content.domain.ContentPageI18n;
import network.foodsharing.content.domain.ContentPageKind;
import network.foodsharing.content.repository.ContentPageI18nRepository;
import network.foodsharing.content.repository.ContentPageRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Content pages, FAQ and learning chapters (plan §13.11, §11.1 step 3). Bodies are markdown (rendered and sanitised
 * by the client). Reads fall back requested language → de → any (A14) and report {@code fallback}. Editors: ORGA and
 * the admins of PR function groups.
 */
@Service
public class ContentService {

    public record LocalizedPage(ContentPage page, String title, String body, String language, boolean fallback,
                                Instant updatedAt, String nextChapterKey) {
    }

    public record PageTexts(ContentPage page, Map<String, String> titles, Map<String, String> bodies) {
    }

    public record PageInput(Map<String, String> titles, Map<String, String> bodies, ContentPageKind kind,
                            Integer sortOrder, Integer estimatedMinutes, boolean published, String reason) {
    }

    static final int SUMMARY_LENGTH = 200;

    private final ContentPageRepository pages;
    private final ContentPageI18nRepository texts;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;

    public ContentService(ContentPageRepository pages, ContentPageI18nRepository texts, RegionService regions,
                          CurrentActorService currentActor, PermissionService permissions, HistoryService history) {
        this.pages = pages;
        this.texts = texts;
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
    }

    /** A published page. @throws ApiException 404 CONTENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public LocalizedPage page(String slug, String language) {
        ContentPage page = pages.findBySlug(slug).filter(ContentPage::isPublished)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.CONTENT_NOT_FOUND));
        return localize(page, language, null);
    }

    /** Published learning chapters in order. */
    @Transactional(readOnly = true)
    public List<LocalizedPage> learningChapters(String language) {
        return publishedChapters().stream().map(page -> localize(page, language, null)).toList();
    }

    /** A learning chapter with the key of the next one. @throws ApiException 404 CONTENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public LocalizedPage learningChapter(String key, String language) {
        List<ContentPage> chapters = publishedChapters();
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).getSlug().equals(key)) {
                return localize(chapters.get(i), language, i + 1 < chapters.size() ? chapters.get(i + 1).getSlug() : null);
            }
        }
        throw ApiException.notFound(ApiErrorCode.CONTENT_NOT_FOUND);
    }

    /** Keys of the published learning chapters (onboarding step 3). */
    @Transactional(readOnly = true)
    public List<String> learningChapterKeys() {
        return publishedChapters().stream().map(ContentPage::getSlug).toList();
    }

    /** First characters of the body without markdown markers. */
    public static String summary(String body) {
        if (body == null) {
            return null;
        }
        String plain = body.replaceAll("(?m)^#+\\s*", "").replaceAll("[*_`>\\[\\]]", "").replaceAll("\\s+", " ").strip();
        return plain.length() <= SUMMARY_LENGTH ? plain : plain.substring(0, SUMMARY_LENGTH - 1).strip() + "…";
    }

    // ---------------------------------------------------------------------------------------------- admin

    public boolean mayEdit(SessionPrincipal actor) {
        return permissions.isOrga(actor) || regions.isFunctionGroupAdminAnywhere(actor.userId(), WorkingGroupFunction.PR);
    }

    /** @throws ApiException 403 unless editor */
    @Transactional(readOnly = true)
    public List<PageTexts> allPages(ContentPageKind kind) {
        requireEditor();
        List<ContentPage> rows = kind == null ? pages.findAllByOrderByKindAscSortOrderAscSlugAsc()
                : pages.findByKindOrderBySortOrderAscSlugAsc(kind);
        Map<UUID, List<ContentPageI18n>> byPage = texts.findByOwnerIdIn(rows.stream().map(ContentPage::getId).toList())
                .stream().collect(Collectors.groupingBy(ContentPageI18n::getOwnerId));
        return rows.stream().map(page -> texts(page, byPage.getOrDefault(page.getId(), List.of()))).toList();
    }

    /** @throws ApiException 403 unless editor, 404 CONTENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public PageTexts pageForEdit(String slug) {
        requireEditor();
        ContentPage page = pages.findBySlug(slug).orElseThrow(() -> ApiException.notFound(ApiErrorCode.CONTENT_NOT_FOUND));
        return texts(page, texts.findByOwnerId(page.getId()));
    }

    /**
     * Creates or updates a page with all its translations (languages missing from the input are removed).
     * Records PAGE_CREATED / PAGE_UPDATED and PAGE_PUBLISHED when it becomes published.
     *
     * @throws ApiException 403 unless editor, 400 VALIDATION_FAILED (no title or a title without body)
     */
    @Transactional
    public PageTexts upsert(String slug, PageInput input) {
        requireEditor();
        if (input.titles() == null || input.titles().isEmpty() || input.bodies() == null
                || !input.bodies().keySet().containsAll(input.titles().keySet())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "titles/bodies");
        }
        ContentPage page = pages.findBySlug(slug).orElse(null);
        boolean created = page == null;
        boolean wasPublished = page != null && page.isPublished();
        if (created) {
            page = new ContentPage();
            page.setSlug(slug);
        }
        page.setKind(input.kind() == null ? ContentPageKind.PAGE : input.kind());
        page.setSortOrder(input.sortOrder() == null ? page.getSortOrder() : input.sortOrder());
        page.setEstimatedMinutes(input.estimatedMinutes());
        page.setPublished(input.published());
        ContentPage saved = pages.saveAndFlush(page);
        Map<String, ContentPageI18n> existing = texts.findByOwnerId(saved.getId()).stream()
                .collect(Collectors.toMap(ContentPageI18n::getLanguage, Function.identity()));
        existing.values().stream().filter(row -> !input.titles().containsKey(row.getLanguage())).forEach(texts::delete);
        input.titles().forEach((language, title) -> {
            ContentPageI18n row = existing.computeIfAbsent(language, l -> {
                ContentPageI18n text = new ContentPageI18n();
                text.setOwnerId(saved.getId());
                text.setLanguage(l);
                return text;
            });
            row.setTitle(title.strip());
            row.setBody(input.bodies().get(language));
            texts.save(row);
        });
        history.record(history.event(created ? ContentAction.PAGE_CREATED : ContentAction.PAGE_UPDATED, saved.getId())
                .reason(input.reason()).metadata(Map.of("slug", slug, "languages",
                        String.join(",", input.titles().keySet()))).build());
        if (input.published() && !wasPublished) {
            history.record(history.event(ContentAction.PAGE_PUBLISHED, saved.getId()).metadata("slug", slug).build());
        }
        return texts(saved, texts.findByOwnerId(saved.getId()));
    }

    private List<ContentPage> publishedChapters() {
        return pages.findByKindOrderBySortOrderAscSlugAsc(ContentPageKind.LEARNING_CHAPTER).stream()
                .filter(ContentPage::isPublished).toList();
    }

    private LocalizedPage localize(ContentPage page, String language, String nextChapterKey) {
        Map<String, ContentPageI18n> byLanguage = texts.findByOwnerId(page.getId()).stream()
                .collect(Collectors.toMap(ContentPageI18n::getLanguage, Function.identity()));
        ContentPageI18n text = pick(byLanguage, language);
        if (text == null) {
            throw ApiException.notFound(ApiErrorCode.CONTENT_NOT_FOUND);
        }
        Instant updated = byLanguage.values().stream().map(ContentPageI18n::getUpdatedAt).filter(Objects::nonNull)
                .max(Instant::compareTo).orElse(page.getUpdatedAt());
        return new LocalizedPage(page, text.getTitle(), text.getBody(), text.getLanguage(),
                !text.getLanguage().equals(language), updated, nextChapterKey);
    }

    private static PageTexts texts(ContentPage page, List<ContentPageI18n> rows) {
        return new PageTexts(page,
                rows.stream().collect(Collectors.toMap(ContentPageI18n::getLanguage, ContentPageI18n::getTitle)),
                rows.stream().collect(Collectors.toMap(ContentPageI18n::getLanguage, ContentPageI18n::getBody)));
    }

    /** requested → de → any (A14). */
    static <T> T pick(Map<String, T> byLanguage, String language) {
        if (byLanguage.isEmpty()) {
            return null;
        }
        if (language != null && byLanguage.containsKey(language)) {
            return byLanguage.get(language);
        }
        return byLanguage.containsKey("de") ? byLanguage.get("de") : byLanguage.values().iterator().next();
    }

    private void requireEditor() {
        if (!mayEdit(currentActor.requirePrincipal())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
