package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.ContentPage;
import network.foodsharing.content.domain.ContentPageI18n;
import network.foodsharing.content.domain.ContentPageKind;
import network.foodsharing.content.repository.ContentPageI18nRepository;
import network.foodsharing.content.repository.ContentPageRepository;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/** C1/B11: new content pages: who may create them, the slug rules and unique slugs (was BlogFlowIT). */
class ContentPageCreateTest {

    ContentPageRepository pages = mock(ContentPageRepository.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    PermissionService permissions = mock(PermissionService.class);
    ContentPageI18nRepository texts = mock(ContentPageI18nRepository.class);
    ContentService service = new ContentService(pages, texts, mock(RegionService.class),
            actors, permissions, mock(HistoryService.class, Answers.RETURNS_DEEP_STUBS), mock(UploadService.class));
    SessionPrincipal orga = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.ORGA, "o@local");
    SessionPrincipal ambassador = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "a@local");

    @BeforeEach
    void setUp() {
        when(permissions.isOrga(orga)).thenReturn(true);
        when(pages.findBySlug(any())).thenReturn(Optional.empty());
        when(texts.saveAndFlush(any())).thenAnswer(invocation -> {
            ContentPageI18n text = invocation.getArgument(0);
            when(texts.findByOwnerId(text.getOwnerId())).thenReturn(java.util.List.of(text));
            return text;
        });
        when(pages.saveAndFlush(any())).thenAnswer(invocation -> {
            ContentPage page = invocation.getArgument(0);
            page.setId(UUID.randomUUID());
            return page;
        });
    }

    @Test
    void orgaCreatesWithAnyFunctionAndTheSlugIsLowerCased() {
        when(actors.requirePrincipal()).thenReturn(orga);
        ContentService.LocalizedPage page = service.create(" Neue-Seite ", ContentPageKind.PAGE, "de", "Neu", "Text",
                "BOARD", null);
        assertThat(page.page().getSlug()).isEqualTo("neue-seite");
        assertThat(page.page().getEditorFunction()).isEqualTo("BOARD");
        assertThat(page.page().isPublished()).isFalse();
    }

    @Test
    void invalidAndTakenSlugsAreRefused() {
        when(actors.requirePrincipal()).thenReturn(orga);
        assertThatThrownBy(() -> service.create("neue seite", ContentPageKind.PAGE, "de", "Neu", "Text", null, null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        when(pages.findBySlug("neue-seite")).thenReturn(Optional.of(new ContentPage()));
        assertThatThrownBy(() -> service.create("Neue-Seite", ContentPageKind.PAGE, "de", "Neu", "Text", null, null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.CONTENT_PAGE_EXISTS);
    }

    @Test
    void anAmbassadorWithoutPrAdministrationMayNotCreate() {
        when(actors.requirePrincipal()).thenReturn(ambassador);
        assertThatThrownBy(() -> service.create("bot-seite", ContentPageKind.PAGE, "de", "Neu", "Text", "BOARD", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verify(pages, never()).saveAndFlush(any());
    }
}
