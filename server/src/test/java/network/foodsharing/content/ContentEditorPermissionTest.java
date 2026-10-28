package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.ContentPage;
import network.foodsharing.content.domain.ContentPageKind;
import network.foodsharing.content.repository.ContentPageI18nRepository;
import network.foodsharing.content.repository.ContentPageRepository;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/** Decision 38 generalised: per-page editor function; only ORGA picks a function other than PR. */
class ContentEditorPermissionTest {

    private final ContentPageRepository pages = mock(ContentPageRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private ContentService service;
    private final SessionPrincipal boardAdmin = principal();
    private final SessionPrincipal prAdmin = principal();
    private final SessionPrincipal orga = principal();

    private static SessionPrincipal principal() {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "x@local");
    }

    @BeforeEach
    void setUp() {
        service = new ContentService(pages, mock(ContentPageI18nRepository.class), regions, actors, permissions,
                mock(HistoryService.class, Answers.RETURNS_DEEP_STUBS), mock(UploadService.class));
        when(permissions.isOrga(orga)).thenReturn(true);
        when(regions.isFunctionGroupAdminAnywhere(boardAdmin.userId(), WorkingGroupFunction.BOARD)).thenReturn(true);
        when(regions.isFunctionGroupAdminAnywhere(prAdmin.userId(), WorkingGroupFunction.PR)).thenReturn(true);
    }

    private static ContentPage page(String function) {
        ContentPage page = new ContentPage();
        page.setId(UUID.randomUUID());
        page.setSlug("p");
        page.setKind(ContentPageKind.PAGE);
        page.setEditorFunction(function);
        return page;
    }

    @Test
    void editorsFollowThePageFunction() {
        ContentPage transparency = page("BOARD");
        ContentPage vision = page("PR");
        ContentPage legacy = page(null);
        assertThat(service.mayEdit(boardAdmin, transparency)).isTrue();
        assertThat(service.mayEdit(prAdmin, transparency)).isFalse();
        assertThat(service.mayEdit(boardAdmin, vision)).isFalse();
        assertThat(service.mayEdit(prAdmin, vision)).isTrue();
        assertThat(service.mayEdit(prAdmin, legacy)).isTrue();
        assertThat(service.mayEdit(orga, transparency)).isTrue();
    }

    @Test
    void anyPageFunctionMakesAnEditor() {
        when(pages.findAll()).thenReturn(List.of(page("BOARD"), page("PR")));
        assertThat(service.mayEdit(boardAdmin)).isTrue();
        assertThat(service.mayEdit(principal())).isFalse();
    }

    @Test
    void onlyOrgaChoosesAnotherFunctionAndUnknownFunctionsAreInvalid() {
        when(actors.requirePrincipal()).thenReturn(prAdmin);
        when(pages.findBySlug(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create("neu", ContentPageKind.PAGE, "de", "Neu", "Text", "BOARD", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> service.create("neu", ContentPageKind.PAGE, "de", "Neu", "Text", "NOPE", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        when(actors.requirePrincipal()).thenReturn(boardAdmin);
        assertThatThrownBy(() -> service.create("neu", ContentPageKind.PAGE, "de", "Neu", "Text", null, null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(actors.requirePrincipal()).thenReturn(prAdmin);
        when(pages.findBySlug("p")).thenReturn(Optional.of(page("BOARD")));
        assertThatThrownBy(() -> service.upsert("p", new ContentService.PageInput(Map.of("de", "T"),
                Map.of("de", "B"), ContentPageKind.PAGE, null, null, true, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }
}
