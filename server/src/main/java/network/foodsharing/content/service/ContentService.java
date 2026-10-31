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
import network.foodsharing.upload.service.UploadService;
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

    /**
     * {@code editorFunction}/{@code imageUploadIds} null = unchanged ({@code imageUploadIds} adds);
     * {@code removeImageUploadIds} removes images of the page and deletes their uploads.
     */
    public record PageInput(Map<String, String> titles, Map<String, String> bodies, ContentPageKind kind,
                            Integer sortOrder, Integer estimatedMinutes, boolean published, String reason,
                            String editorFunction, List<UUID> imageUploadIds, List<UUID> removeImageUploadIds) {

        public PageInput(Map<String, String> titles, Map<String, String> bodies, ContentPageKind kind,
                         Integer sortOrder, Integer estimatedMinutes, boolean published, String reason,
                         String editorFunction, List<UUID> imageUploadIds) {
            this(titles, bodies, kind, sortOrder, estimatedMinutes, published, reason, editorFunction,
                    imageUploadIds, null);
        }

        public PageInput(Map<String, String> titles, Map<String, String> bodies, ContentPageKind kind,
                         Integer sortOrder, Integer estimatedMinutes, boolean published, String reason) {
            this(titles, bodies, kind, sortOrder, estimatedMinutes, published, reason, null, null, null);
        }
    }

    public static final String IMAGE_MODULE = "CONTENT_IMAGE";
    static final String DEFAULT_EDITOR_FUNCTION = WorkingGroupFunction.PR.name();

    static final int SUMMARY_LENGTH = 200;

    private final ContentPageRepository pages;
    private final ContentPageI18nRepository texts;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final UploadService uploads;

    public ContentService(ContentPageRepository pages, ContentPageI18nRepository texts, RegionService regions,
                          CurrentActorService currentActor, PermissionService permissions, HistoryService history,
                          UploadService uploads) {
        this.uploads = uploads;
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

    /** A published page, empty when missing or unpublished (no exception inside surrounding transactions). */
    @Transactional(readOnly = true)
    public java.util.Optional<LocalizedPage> findPublished(String slug, String language) {
        return pages.findBySlug(slug).filter(ContentPage::isPublished)
                .filter(page -> !texts.findByOwnerId(page.getId()).isEmpty())
                .map(page -> localize(page, language, null));
    }

    /** Published FAQ entries (pages of kind FAQ) in sort order. */
    @Transactional(readOnly = true)
    public List<LocalizedPage> faq(String language) {
        return pages.findByKindOrderBySortOrderAscSlugAscIdAsc(ContentPageKind.FAQ).stream().filter(ContentPage::isPublished)
                .filter(page -> !texts.findByOwnerId(page.getId()).isEmpty())
                .map(page -> localize(page, language, null)).toList();
    }

    /** Does the page still exist (upload cleanup of CONTENT_IMAGE)? */
    @Transactional(readOnly = true)
    public boolean exists(UUID pageId) {
        return pages.existsById(pageId);
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

    /** Editor of at least one page: ORGA, PR group admins, admins of a group holding a page's editor function. */
    public boolean mayEdit(SessionPrincipal actor) {
        if (permissions.isOrga(actor) || regions.isFunctionGroupAdminAnywhere(actor.userId(), WorkingGroupFunction.PR)) {
            return true;
        }
        return pages.findAll().stream().map(ContentService::editorFunction).distinct()
                .anyMatch(function -> regions.isFunctionGroupAdminAnywhere(actor.userId(), function));
    }

    /** Per-page editors (decision 38 generalised): ORGA or admins of a group holding the page's editor function. */
    public boolean mayEdit(SessionPrincipal actor, ContentPage page) {
        return permissions.isOrga(actor) || regions.isFunctionGroupAdminAnywhere(actor.userId(), editorFunction(page));
    }

    /** The page's editor function; unknown or empty values fall back to PR. */
    static WorkingGroupFunction editorFunction(ContentPage page) {
        return function(page.getEditorFunction());
    }

    /** Editor function from a request: null/blank = PR, unknown names → 400 VALIDATION_FAILED. */
    static WorkingGroupFunction requestedFunction(String value) {
        if (value == null || value.isBlank()) {
            return WorkingGroupFunction.PR;
        }
        try {
            return WorkingGroupFunction.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "editorFunction");
        }
    }

    static WorkingGroupFunction function(String value) {
        if (value == null || value.isBlank()) {
            return WorkingGroupFunction.PR;
        }
        try {
            return WorkingGroupFunction.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return WorkingGroupFunction.PR;
        }
    }

    /** Pages the current user may edit. @throws ApiException 403 unless editor of at least one page */
    @Transactional(readOnly = true)
    public List<PageTexts> allPages(ContentPageKind kind) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        requireEditor();
        List<ContentPage> rows = (kind == null ? pages.findAllByOrderByKindAscSortOrderAscSlugAscIdAsc()
                : pages.findByKindOrderBySortOrderAscSlugAscIdAsc(kind)).stream().filter(page -> mayEdit(actor, page))
                .toList();
        Map<UUID, List<ContentPageI18n>> byPage = texts.findByOwnerIdIn(rows.stream().map(ContentPage::getId).toList())
                .stream().collect(Collectors.groupingBy(ContentPageI18n::getOwnerId));
        return rows.stream().map(page -> texts(page, byPage.getOrDefault(page.getId(), List.of()))).toList();
    }

    /** @throws ApiException 403 unless editor, 404 CONTENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public PageTexts pageForEdit(String slug) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        ContentPage page = pages.findBySlug(slug).orElse(null);
        if (page == null) {
            requireEditor();
            throw ApiException.notFound(ApiErrorCode.CONTENT_NOT_FOUND);
        }
        if (!mayEdit(actor, page)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return texts(page, texts.findByOwnerId(page.getId()));
    }

    /**
     * Creates a page with one text in {@code language} (B11). Only ORGA sets an editor function other than PR.
     *
     * @throws ApiException 403 unless ORGA or PR group admin, 409 CONTENT_PAGE_EXISTS
     */
    @Transactional
    public LocalizedPage create(String slug, ContentPageKind kind, String language, String title, String body,
                                String editorFunction, List<UUID> imageUploadIds) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean orga = permissions.isOrga(actor);
        if (!orga && !regions.isFunctionGroupAdminAnywhere(actor.userId(), WorkingGroupFunction.PR)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        String normalisedSlug = slug.strip().toLowerCase(java.util.Locale.ROOT);
        if (!normalisedSlug.matches("[a-z0-9][a-z0-9-]*")) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "slug");
        }
        if (pages.findBySlug(normalisedSlug).isPresent()) {
            throw ApiException.conflict(ApiErrorCode.CONTENT_PAGE_EXISTS);
        }
        WorkingGroupFunction function = requestedFunction(editorFunction);
        if (function != WorkingGroupFunction.PR && !orga) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        ContentPage page = new ContentPage();
        page.setSlug(normalisedSlug);
        page.setKind(kind == null ? ContentPageKind.PAGE : kind);
        page.setEditorFunction(function.name());
        page.setPublished(false);
        ContentPage saved = pages.saveAndFlush(page);
        ContentPageI18n text = new ContentPageI18n();
        text.setOwnerId(saved.getId());
        text.setLanguage(language == null ? "de" : language);
        text.setTitle(title.strip());
        text.setBody(body == null ? "" : body);
        texts.saveAndFlush(text);
        claimImages(saved, imageUploadIds, actor.userId());
        history.record(history.event(ContentAction.PAGE_CREATED, saved.getId())
                .metadata(Map.of("slug", normalisedSlug, "languages", text.getLanguage())).build());
        return localize(saved, text.getLanguage(), null);
    }

    private void claimImages(ContentPage page, List<UUID> uploadIds, UUID actorId) {
        if (uploadIds == null) {
            return;
        }
        java.util.LinkedHashSet<String> current = new java.util.LinkedHashSet<>(imageIds(page));
        for (UUID uploadId : new java.util.LinkedHashSet<>(uploadIds)) {
            if (current.contains(uploadId.toString())) {
                continue;
            }
            network.foodsharing.upload.domain.Upload upload = uploads.find(uploadId);
            if (!actorId.equals(upload.getOwnerUserId()) || upload.getMimeType() == null
                    || !upload.getMimeType().startsWith("image/")) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
            uploads.claim(uploadId, new network.foodsharing.upload.service.UploadUsage(IMAGE_MODULE, page.getId()));
            current.add(uploadId.toString());
        }
        String joined = String.join(",", current);
        if (joined.length() > 2000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "imageUploadIds");
        }
        page.setImageUploadIds(joined.isEmpty() ? null : joined);
    }

    /** Drops {@code uploadIds} from the page's images and deletes their uploads; returns the removed ids. */
    private List<String> removeImages(ContentPage page, List<UUID> uploadIds) {
        if (uploadIds.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> current = new java.util.LinkedHashSet<>(imageIds(page));
        List<String> removed = new java.util.ArrayList<>();
        for (UUID uploadId : new java.util.LinkedHashSet<>(uploadIds)) {
            if (current.remove(uploadId.toString())) {
                uploads.delete(uploads.find(uploadId));
                removed.add(uploadId.toString());
            }
        }
        page.setImageUploadIds(current.isEmpty() ? null : String.join(",", current));
        return removed;
    }

    /** Claimed image uploads of a page (editor view). */
    public static List<UUID> imageUploadIds(ContentPage page) {
        return imageIds(page).stream().map(UUID::fromString).toList();
    }

    static List<String> imageIds(ContentPage page) {
        return page.getImageUploadIds() == null || page.getImageUploadIds().isBlank() ? List.of()
                : List.of(page.getImageUploadIds().split(","));
    }

    /**
     * Creates or updates a page with all its translations (languages missing from the input are removed).
     * Records PAGE_CREATED / PAGE_UPDATED and PAGE_PUBLISHED when it becomes published.
     *
     * @throws ApiException 403 unless editor, 400 VALIDATION_FAILED (no title or a title without body)
     */
    @Transactional
    public PageTexts upsert(String slug, PageInput input) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (input.titles() == null || input.titles().isEmpty() || input.bodies() == null
                || !input.bodies().keySet().containsAll(input.titles().keySet())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "titles/bodies");
        }
        ContentPage page = pages.findBySlug(slug).orElse(null);
        boolean created = page == null;
        if (created ? !mayEdit(actor, new ContentPage()) : !mayEdit(actor, page)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (input.editorFunction() != null && !permissions.isOrga(actor)
                && (created ? requestedFunction(input.editorFunction()) != WorkingGroupFunction.PR
                : requestedFunction(input.editorFunction()) != editorFunction(page))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        boolean wasPublished = page != null && page.isPublished();
        if (created) {
            page = new ContentPage();
            page.setSlug(slug);
        }
        page.setKind(input.kind() == null ? ContentPageKind.PAGE : input.kind());
        page.setSortOrder(input.sortOrder() == null ? page.getSortOrder() : input.sortOrder());
        page.setEstimatedMinutes(input.estimatedMinutes());
        page.setPublished(input.published());
        if (input.editorFunction() != null) {
            page.setEditorFunction(requestedFunction(input.editorFunction()).name());
        }
        List<UUID> remove = input.removeImageUploadIds() == null ? List.of() : input.removeImageUploadIds();
        if (!remove.isEmpty() && (input.imageUploadIds() != null && remove.stream()
                .anyMatch(input.imageUploadIds()::contains)
                || !imageIds(page).containsAll(remove.stream().map(UUID::toString).toList()))) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "removeImageUploadIds");
        }
        ContentPage saved = pages.saveAndFlush(page);
        claimImages(saved, input.imageUploadIds(), actor.userId());
        List<String> removedImages = removeImages(saved, remove);
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
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("slug", slug);
        metadata.put("languages", String.join(",", input.titles().keySet()));
        if (!removedImages.isEmpty()) {
            metadata.put("removedImages", String.join(",", removedImages));
        }
        history.record(history.event(created ? ContentAction.PAGE_CREATED : ContentAction.PAGE_UPDATED, saved.getId())
                .reason(input.reason()).metadata(metadata).build());
        if (input.published() && !wasPublished) {
            history.record(history.event(ContentAction.PAGE_PUBLISHED, saved.getId()).metadata("slug", slug).build());
        }
        return texts(saved, texts.findByOwnerId(saved.getId()));
    }

    private List<ContentPage> publishedChapters() {
        return pages.findByKindOrderBySortOrderAscSlugAscIdAsc(ContentPageKind.LEARNING_CHAPTER).stream()
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
