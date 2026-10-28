package network.foodsharing.content.service;

import java.util.Set;
import java.util.UUID;

/**
 * Blog permissions (upstream {@code BlogPermissions}), pure. Editors (ORGA or PR group admins) administrate every
 * post and publish directly; ambassadors create and edit posts of their regions but only request a check.
 */
public final class BlogRules {

    /** Facts about the acting user. */
    public record Actor(UUID userId, boolean editor, Set<UUID> ambassadorOf) {
    }

    /** Facts about a post. */
    public record Post(UUID regionId, UUID authorId, boolean published) {
    }

    private BlogRules() {
    }

    /** Editors anywhere (also global posts); ambassadors only for a region they administrate. */
    public static boolean mayCreate(Actor actor, UUID regionId) {
        return actor.editor() || (regionId != null && actor.ambassadorOf().contains(regionId));
    }

    /** Editors; ambassadors of the post's region; the author of a not yet published post. */
    public static boolean mayEdit(Actor actor, Post post) {
        return actor.editor()
                || (post.regionId() != null && actor.ambassadorOf().contains(post.regionId()))
                || (!post.published() && actor.userId().equals(post.authorId()));
    }

    /** Only editors publish (and unpublish); everybody else who may edit requests a check. */
    public static boolean mayPublish(Actor actor) {
        return actor.editor();
    }

    public static boolean mayDelete(Actor actor, Post post) {
        return mayEdit(actor, post);
    }

    /** May see the post in the editor list (drafts): whoever may edit it. */
    public static boolean maySeeDraft(Actor actor, Post post) {
        return mayEdit(actor, post);
    }

    /** A non-editor's change of a published post takes it offline again until an editor checks it (upstream). */
    public static boolean changeNeedsCheck(Actor actor, Post post) {
        return !actor.editor() && post.published();
    }
}
