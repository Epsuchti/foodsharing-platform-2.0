package network.foodsharing.content.web;

import java.util.LinkedHashMap;
import network.foodsharing.api.model.ConsentStatus;
import network.foodsharing.api.model.ContentPage;
import network.foodsharing.api.model.ContentPageKind;
import network.foodsharing.api.model.ContentPageSummary;
import network.foodsharing.api.model.ContentPageUpdate;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.LearningChapter;
import network.foodsharing.api.model.LegalDocument;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.api.model.LegalVersionView;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.content.domain.LegalDocumentVersion;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.LegalService;

/** Content / legal service views → generated models (shared by the four content controllers). */
final class ContentModels {

    private ContentModels() {
    }

    static ContentPage page(ContentService.LocalizedPage view) {
        ContentPage page = new ContentPage();
        page.setSlug(view.page().getSlug());
        page.setKind(ContentPageKind.fromValue(view.page().getKind().name()));
        page.setTitle(view.title());
        page.setBody(view.body());
        page.setLanguage(Language.fromValue(view.language()));
        page.setFallback(view.fallback());
        page.setUpdatedAt(TimeMapper.toOffset(view.updatedAt()));
        page.setEstimatedMinutes(view.page().getEstimatedMinutes());
        page.setNextChapterKey(view.nextChapterKey());
        return page;
    }

    static LearningChapter chapter(ContentService.LocalizedPage view) {
        LearningChapter chapter = new LearningChapter();
        chapter.setKey(view.page().getSlug());
        chapter.setTitle(view.title());
        chapter.setSummary(ContentService.summary(view.body()));
        chapter.setEstimatedMinutes(view.page().getEstimatedMinutes() == null ? 0 : view.page().getEstimatedMinutes());
        chapter.setSortOrder(view.page().getSortOrder());
        chapter.setLanguage(Language.fromValue(view.language()));
        return chapter;
    }

    static ContentPageSummary summary(ContentService.PageTexts texts) {
        ContentPageSummary summary = new ContentPageSummary();
        summary.setSlug(texts.page().getSlug());
        summary.setKind(ContentPageKind.fromValue(texts.page().getKind().name()));
        summary.setTitles(new LinkedHashMap<>(texts.titles()));
        summary.setLanguages(texts.titles().keySet().stream().sorted().map(Language::fromValue).toList());
        summary.setPublished(texts.page().isPublished());
        summary.setSortOrder(texts.page().getSortOrder());
        summary.setEstimatedMinutes(texts.page().getEstimatedMinutes());
        summary.setUpdatedAt(TimeMapper.toOffset(texts.page().getUpdatedAt()));
        return summary;
    }

    static ContentPageUpdate edit(ContentService.PageTexts texts) {
        ContentPageUpdate update = new ContentPageUpdate();
        update.setTitles(new LinkedHashMap<>(texts.titles()));
        update.setBodies(new LinkedHashMap<>(texts.bodies()));
        update.setKind(ContentPageKind.fromValue(texts.page().getKind().name()));
        update.setSortOrder(texts.page().getSortOrder());
        update.setEstimatedMinutes(texts.page().getEstimatedMinutes());
        update.setPublished(texts.page().isPublished());
        return update;
    }

    static LegalDocument document(LegalService.Document document) {
        LegalDocument model = new LegalDocument();
        model.setKind(LegalDocumentKind.fromValue(document.version().getKind().name()));
        model.setVersion(document.version().getDocumentVersion());
        model.setPublishedAt(TimeMapper.toOffset(document.version().getPublishedAt()));
        model.setSummary(document.summary());
        model.setBody(document.body());
        model.setLanguage(Language.fromValue(document.language()));
        model.setFallback(document.fallback());
        return model;
    }

    static ConsentStatus consent(LegalService.Consent consent) {
        ConsentStatus status = new ConsentStatus();
        status.setKind(LegalDocumentKind.fromValue(consent.kind().name()));
        status.setCurrentVersion(consent.currentVersion());
        status.setAcceptedVersion(consent.acceptedVersion());
        status.setAcceptedAt(TimeMapper.toOffset(consent.acceptedAt()));
        status.setRequired(consent.required());
        status.setPending(consent.pending());
        return status;
    }

    static LegalVersionView version(LegalDocumentVersion version) {
        LegalVersionView view = new LegalVersionView();
        view.setKind(LegalDocumentKind.fromValue(version.getKind().name()));
        view.setVersion(version.getDocumentVersion());
        view.setPublishedAt(TimeMapper.toOffset(version.getPublishedAt()));
        view.setRequireReconsent(version.isRequireReconsent());
        return view;
    }

    static network.foodsharing.content.domain.LegalDocumentKind kind(LegalDocumentKind kind) {
        return network.foodsharing.content.domain.LegalDocumentKind.valueOf(kind.name());
    }

    static String language(Language language) {
        return language == null ? null : language.getValue();
    }
}
