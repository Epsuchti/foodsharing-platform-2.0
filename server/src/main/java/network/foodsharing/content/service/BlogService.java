package network.foodsharing.content.service;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.BlogPost;
import network.foodsharing.content.domain.ContentAction;
import network.foodsharing.content.repository.BlogPostRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Blog (C2, upstream {@code BlogController}/{@code BlogPermissions}): editors (ORGA, PR group admins) publish;
 * ambassadors write posts of their regions and request a check ({@code BLOG_NEW_CHECK} bell to the PR admins).
 * Pictures are claimed as {@code BLOG_PICTURE} (public). Every operation needs the BLOG feature toggle.
 */
@Service
public class BlogService {

    public static final String PICTURE_MODULE = "BLOG_PICTURE";

    public record PostInput(String title, String teaser, String body, UUID regionId, UUID pictureUploadId) {
    }

    /** A post with what the current user may do with it. */
    public record PostView(BlogPost post, boolean mayEdit, boolean mayPublish) {
    }

    private final BlogPostRepository posts;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final NotificationService notifications;
    private final UploadService uploads;
    private final FeatureToggleService toggles;
    private final HistoryService history;
    private final Clock clock;

    public BlogService(BlogPostRepository posts, RegionService regions, CurrentActorService currentActor,
                       PermissionService permissions, NotificationService notifications, UploadService uploads,
                       FeatureToggleService toggles, HistoryService history, Clock clock) {
        this.posts = posts;
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.notifications = notifications;
        this.uploads = uploads;
        this.toggles = toggles;
        this.history = history;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------- public

    /** Published posts, newest first, optionally of one region. */
    @Transactional(readOnly = true)
    public Page<BlogPost> published(UUID regionId, int page, int size) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        Pageable pageable = pageable(page, size);
        return regionId == null ? posts.findPublished(pageable) : posts.findPublishedOfRegion(regionId, pageable);
    }

    /** @throws ApiException 404 BLOG_POST_NOT_FOUND unless published */
    @Transactional(readOnly = true)
    public BlogPost publishedPost(UUID postId) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogPost post = require(postId);
        if (!post.isPublished()) {
            throw ApiException.notFound(ApiErrorCode.BLOG_POST_NOT_FOUND);
        }
        return post;
    }

    // ------------------------------------------------------------------------------------------- editing

    /** Posts the current user may edit (editors: all), newest first. @throws ApiException 403 for nobody */
    @Transactional(readOnly = true)
    public Page<PostView> editable(int page, int size) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogRules.Actor actor = actor();
        if (!actor.editor() && actor.ambassadorOf().isEmpty()) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        Page<BlogPost> rows = actor.editor() ? posts.findAllActive(pageable(page, size))
                : posts.findActiveOfAuthorOrRegions(actor.userId(), actor.ambassadorOf(), pageable(page, size));
        return rows.map(post -> view(actor, post));
    }

    /** @throws ApiException 404 BLOG_POST_NOT_FOUND, 403 unless the user may edit or it is published */
    @Transactional(readOnly = true)
    public PostView get(UUID postId) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogPost post = require(postId);
        BlogRules.Actor actor = actor();
        if (!post.isPublished() && !BlogRules.maySeeDraft(actor, facts(post))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return view(actor, post);
    }

    /**
     * Creates an unpublished post. @throws ApiException 403 unless editor or ambassador of the region,
     * 404 REGION_NOT_FOUND, UPLOAD_NOT_FOUND
     */
    @Transactional
    public PostView create(PostInput input) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogRules.Actor actor = actor();
        if (!BlogRules.mayCreate(actor, input.regionId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (input.regionId() != null) {
            regions.require(input.regionId());
        }
        BlogPost post = new BlogPost();
        post.setAuthorId(actor.userId());
        post.setRegionId(input.regionId());
        post.setTitle(input.title().strip());
        post.setTeaser(input.teaser() == null ? "" : input.teaser().strip());
        post.setBody(input.body());
        BlogPost saved = posts.saveAndFlush(post);
        if (input.pictureUploadId() != null) {
            claimPicture(saved, input.pictureUploadId(), actor.userId());
        }
        history.record(history.event(ContentAction.BLOG_CREATED, saved.getId()).region(saved.getRegionId())
                .metadata("title", saved.getTitle()).build());
        return view(actor, saved);
    }

    /**
     * Changes a post (null = unchanged). A non-editor's change of a published post unpublishes it and requests a
     * check (upstream). @throws ApiException 403, 404 BLOG_POST_NOT_FOUND
     */
    @Transactional
    public PostView update(UUID postId, PostInput input) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogPost post = require(postId);
        BlogRules.Actor actor = actor();
        if (!BlogRules.mayEdit(actor, facts(post))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (input.regionId() != null && !input.regionId().equals(post.getRegionId())) {
            if (!BlogRules.mayCreate(actor, input.regionId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
            regions.require(input.regionId());
            post.setRegionId(input.regionId());
        }
        if (input.title() != null) {
            post.setTitle(input.title().strip());
        }
        if (input.teaser() != null) {
            post.setTeaser(input.teaser().strip());
        }
        if (input.body() != null) {
            post.setBody(input.body());
        }
        if (input.pictureUploadId() != null && !input.pictureUploadId().equals(post.getPictureUploadId())) {
            UUID previous = post.getPictureUploadId();
            claimPicture(post, input.pictureUploadId(), actor.userId());
            if (previous != null) {
                uploads.delete(uploads.find(previous));
            }
        }
        boolean needsCheck = BlogRules.changeNeedsCheck(actor, facts(post));
        if (needsCheck) {
            post.setPublished(false);
        }
        history.record(history.event(ContentAction.BLOG_UPDATED, post.getId()).region(post.getRegionId())
                .metadata("title", post.getTitle()).build());
        if (needsCheck) {
            history.record(history.event(ContentAction.BLOG_UNPUBLISHED, post.getId()).region(post.getRegionId())
                    .metadata("reason", "changed by a non-editor").build());
            requestCheck(post, actor.userId());
        }
        return view(actor, post);
    }

    /**
     * Editors publish / unpublish. Anybody else who may edit only requests a check: the post stays unpublished,
     * {@code awaitingCheck} is set and the PR admins get a {@code BLOG_NEW_CHECK} bell.
     *
     * @throws ApiException 403, 404 BLOG_POST_NOT_FOUND
     */
    @Transactional
    public PostView setPublished(UUID postId, boolean published) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogPost post = require(postId);
        BlogRules.Actor actor = actor();
        if (!BlogRules.mayEdit(actor, facts(post))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (!BlogRules.mayPublish(actor)) {
            if (published && !post.isPublished() && !post.isAwaitingCheck()) {
                history.record(history.event(ContentAction.BLOG_UPDATED, post.getId()).region(post.getRegionId())
                        .metadata("check", "requested").build());
                requestCheck(post, actor.userId());
            } else if (!published && post.isAwaitingCheck()) {
                post.setAwaitingCheck(false);
                notifications.removeBells(checkBell(post.getId()));
                history.record(history.event(ContentAction.BLOG_UPDATED, post.getId()).region(post.getRegionId())
                        .metadata("check", "withdrawn").build());
            } else if (!published && post.isPublished()) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
            return view(actor, post);
        }
        if (published != post.isPublished()) {
            post.setPublished(published);
            if (published) {
                post.setPublishedAt(clock.instant());
            }
            history.record(history.event(published ? ContentAction.BLOG_PUBLISHED : ContentAction.BLOG_UNPUBLISHED,
                    post.getId()).region(post.getRegionId()).metadata("title", post.getTitle()).build());
        }
        if (post.isAwaitingCheck()) {
            post.setAwaitingCheck(false);
            notifications.removeBells(checkBell(post.getId()));
        }
        return view(actor, post);
    }

    /** Soft delete; the picture goes with the next upload cleanup. @throws ApiException 403, 404 */
    @Transactional
    public void delete(UUID postId) {
        toggles.requireEnabled(FeatureToggle.BLOG);
        BlogPost post = require(postId);
        if (!BlogRules.mayDelete(actor(), facts(post))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        post.setDeletedAt(clock.instant());
        post.setPublished(false);
        post.setAwaitingCheck(false);
        notifications.removeBells(checkBell(post.getId()));
        history.record(history.event(ContentAction.BLOG_DELETED, post.getId()).region(post.getRegionId())
                .metadata("title", post.getTitle()).build());
    }

    /** Is the post still there (upload cleanup)? */
    @Transactional(readOnly = true)
    public boolean exists(UUID postId) {
        return posts.findById(postId).filter(post -> post.getDeletedAt() == null).isPresent();
    }

    /** Blog editors: ORGA or admins of a PR working group (any region). */
    public boolean isEditor(SessionPrincipal principal) {
        return permissions.isOrga(principal)
                || regions.isFunctionGroupAdminAnywhere(principal.userId(), WorkingGroupFunction.PR);
    }

    static String checkBell(UUID postId) {
        return "blog-check-" + postId;
    }

    // ------------------------------------------------------------------------------------------- helpers

    private void requestCheck(BlogPost post, UUID requesterId) {
        post.setAwaitingCheck(true);
        Set<UUID> recipients = new TreeSet<>(regions.functionGroupAdminIds(WorkingGroupFunction.PR));
        recipients.remove(requesterId);
        notifications.removeBells(checkBell(post.getId()));
        if (!recipients.isEmpty()) {
            notifications.notify(NotificationRequest.to(recipients, NotificationType.BLOG_NEW_CHECK)
                    .param("title", post.getTitle()).link("/blog/" + post.getId() + "/edit")
                    .identifier(checkBell(post.getId())).build());
        }
    }

    private void claimPicture(BlogPost post, UUID uploadId, UUID actorId) {
        Upload upload = uploads.find(uploadId);
        if (!Objects.equals(actorId, upload.getOwnerUserId()) && !Objects.equals(post.getPictureUploadId(), uploadId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        uploads.claim(uploadId, new UploadUsage(PICTURE_MODULE, post.getId()));
        post.setPictureUploadId(uploadId);
    }

    private BlogPost require(UUID postId) {
        return posts.findById(postId).filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BLOG_POST_NOT_FOUND));
    }

    private BlogRules.Actor actor() {
        SessionPrincipal principal = currentActor.requirePrincipal();
        return new BlogRules.Actor(principal.userId(), isEditor(principal), regions.adminRegionIds(principal.userId()));
    }

    private PostView view(BlogRules.Actor actor, BlogPost post) {
        return new PostView(post, BlogRules.mayEdit(actor, facts(post)), BlogRules.mayPublish(actor));
    }

    private static BlogRules.Post facts(BlogPost post) {
        return new BlogRules.Post(post.getRegionId(), post.getAuthorId(), post.isPublished());
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100));
    }
}
