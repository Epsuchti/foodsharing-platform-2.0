package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/** B11: page images are added by imageUploadIds and removed (upload deleted) by removeImageUploadIds. */
class ContentPageImagesTest {

    private final ContentPageRepository pages = mock(ContentPageRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final UploadService uploads = mock(UploadService.class);
    private final HistoryService history = mock(HistoryService.class, Answers.RETURNS_DEEP_STUBS);
    private final SessionPrincipal prAdmin = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Role.AMBASSADOR, "pr@local");
    private final UUID kept = UUID.randomUUID();
    private final UUID dropped = UUID.randomUUID();
    private final Upload droppedUpload = new Upload();
    private final ContentPage page = new ContentPage();
    private ContentService service;

    @BeforeEach
    void setUp() {
        service = new ContentService(pages, mock(ContentPageI18nRepository.class), regions, actors,
                mock(PermissionService.class), history, uploads);
        when(actors.requirePrincipal()).thenReturn(prAdmin);
        when(regions.isFunctionGroupAdminAnywhere(prAdmin.userId(), WorkingGroupFunction.PR)).thenReturn(true);
        page.setId(UUID.randomUUID());
        page.setSlug("vision");
        page.setKind(ContentPageKind.PAGE);
        page.setEditorFunction("PR");
        page.setImageUploadIds(kept + "," + dropped);
        when(pages.findBySlug("vision")).thenReturn(Optional.of(page));
        when(pages.saveAndFlush(any(ContentPage.class))).thenAnswer(i -> i.getArgument(0));
        when(uploads.find(dropped)).thenReturn(droppedUpload);
    }

    private ContentService.PageInput input(List<UUID> add, List<UUID> remove) {
        return new ContentService.PageInput(Map.of("de", "Vision"), Map.of("de", "Text"), ContentPageKind.PAGE, null,
                null, true, null, null, add, remove);
    }

    @Test
    void removedImagesLeaveThePageAndTheirUploadsAreDeleted() {
        service.upsert("vision", input(null, List.of(dropped)));
        assertThat(ContentService.imageUploadIds(page)).containsExactly(kept);
        verify(uploads).delete(droppedUpload);
    }

    @Test
    void anImageThatIsNotOnThePageOrAddedAtTheSameTimeIsRejected() {
        UUID foreign = UUID.randomUUID();
        assertThatThrownBy(() -> service.upsert("vision", input(null, List.of(foreign))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service.upsert("vision", input(List.of(dropped), List.of(dropped))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(ContentService.imageUploadIds(page)).containsExactly(kept, dropped);
        verify(uploads, never()).delete(any());
    }
}
