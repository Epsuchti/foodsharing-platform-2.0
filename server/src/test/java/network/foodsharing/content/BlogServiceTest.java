package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.BlogPost;
import network.foodsharing.content.domain.ContentAction;
import network.foodsharing.content.repository.BlogPostRepository;
import network.foodsharing.content.service.BlogService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.data.domain.Pageable;

/** Blog publish flow: new check by ambassadors, publishing by editors, public filter, BLOG toggle. */
class BlogServiceTest {

    private static final UUID REGION = UUID.randomUUID();
    private static final UUID AMBASSADOR = UUID.randomUUID();
    private static final UUID EDITOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final BlogPostRepository posts = mock(BlogPostRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FeatureToggleService toggles = mock(FeatureToggleService.class);
    private final HistoryService history = mock(HistoryService.class, Answers.RETURNS_DEEP_STUBS);
    private final UploadService uploads = mock(UploadService.class);
    private BlogService service;
    private BlogPost post;

    @BeforeEach
    void setUp() {
        service = new BlogService(posts, regions, actors, permissions, notifications, uploads,
                toggles, history, Clock.fixed(NOW, ZoneOffset.UTC));
        post = new BlogPost();
        post.setId(UUID.randomUUID());
        post.setRegionId(REGION);
        post.setAuthorId(AMBASSADOR);
        post.setTitle("Hallo");
        post.setTeaser("");
        post.setBody("Text");
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(regions.adminRegionIds(AMBASSADOR)).thenReturn(Set.of(REGION));
        when(regions.adminRegionIds(EDITOR)).thenReturn(Set.of());
        when(regions.isFunctionGroupAdminAnywhere(EDITOR, WorkingGroupFunction.PR)).thenReturn(true);
        when(regions.functionGroupAdminIds(WorkingGroupFunction.PR)).thenReturn(Set.of(EDITOR));
    }

    private void actAs(UUID userId) {
        when(actors.requirePrincipal()).thenReturn(new SessionPrincipal(userId, UUID.randomUUID(), Role.AMBASSADOR,
                "x@local"));
    }

    @Test
    void ambassadorPublishRequestsACheckAndBellsTheEditors() {
        actAs(AMBASSADOR);
        BlogService.PostView view = service.setPublished(post.getId(), true);
        assertThat(view.post().isPublished()).isFalse();
        assertThat(view.post().isAwaitingCheck()).isTrue();
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().recipientIds()).containsExactly(EDITOR);
    }

    @Test
    void editorPublishesAndClearsTheCheck() {
        post.setAwaitingCheck(true);
        actAs(EDITOR);
        BlogService.PostView view = service.setPublished(post.getId(), true);
        assertThat(view.post().isPublished()).isTrue();
        assertThat(view.post().getPublishedAt()).isEqualTo(NOW);
        assertThat(view.post().isAwaitingCheck()).isFalse();
        verify(notifications).removeBells(anyString());
        verify(notifications, never()).notify(any());
    }

    @Test
    void ambassadorMayNotUnpublishAndAmbassadorEditsTakePostsOffline() {
        post.setPublished(true);
        actAs(AMBASSADOR);
        assertThatThrownBy(() -> service.setPublished(post.getId(), false)).isInstanceOf(ApiException.class);
        service.update(post.getId(), new BlogService.PostInput("Neu", null, null, null, null));
        assertThat(post.isPublished()).isFalse();
        assertThat(post.isAwaitingCheck()).isTrue();
    }

    @Test
    void removePictureDeletesTheUploadButNotTogetherWithANewOne() {
        UUID picture = UUID.randomUUID();
        post.setPictureUploadId(picture);
        network.foodsharing.upload.domain.Upload upload = new network.foodsharing.upload.domain.Upload();
        when(uploads.find(picture)).thenReturn(upload);
        actAs(EDITOR);
        assertThatThrownBy(() -> service.update(post.getId(),
                new BlogService.PostInput(null, null, null, null, UUID.randomUUID(), true)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(post.getPictureUploadId()).isEqualTo(picture);

        service.update(post.getId(), new BlogService.PostInput(null, null, null, null, null, true));
        assertThat(post.getPictureUploadId()).isNull();
        verify(uploads).delete(upload);
        verify(history.event(ContentAction.BLOG_UPDATED, post.getId()).region(REGION).metadata("title", "Hallo"))
                .metadata("pictureRemoved", picture.toString());

        // without a picture there is nothing to delete
        service.update(post.getId(), new BlogService.PostInput(null, null, null, null, null, true));
        verify(uploads, org.mockito.Mockito.times(1)).delete(any());
    }

    @Test
    void strangersAreRejectedAndDeletedPostsAreGone() {
        actAs(UUID.randomUUID());
        assertThatThrownBy(() -> service.update(post.getId(), new BlogService.PostInput("x", null, null, null, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
        post.setDeletedAt(NOW);
        assertThat(service.exists(post.getId())).isFalse();
        assertThatThrownBy(() -> service.publishedPost(post.getId())).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ApiErrorCode.BLOG_POST_NOT_FOUND);
    }

    @Test
    void publicListUsesThePublishedQueriesAndTheToggle() {
        service.published(null, 0, 500);
        verify(posts).findPublished(any(Pageable.class));
        service.published(REGION, 0, 10);
        verify(posts).findPublishedOfRegion(eq(REGION), any(Pageable.class));
        doThrow(ApiException.notFound(ApiErrorCode.FEATURE_DISABLED)).when(toggles).requireEnabled(FeatureToggle.BLOG);
        assertThatThrownBy(() -> service.published(null, 0, 10)).isInstanceOf(ApiException.class);
    }

    @Test
    void unpublishedPostIsNotPublic() {
        assertThatThrownBy(() -> service.publishedPost(post.getId())).isInstanceOf(ApiException.class);
    }
}
