package network.foodsharing.forum.service;

import java.util.UUID;
import network.foodsharing.forum.domain.ThreadStatus;

/**
 * Pure forum permission rules (A36, upstream {@code ForumPermissions}); {@link ForumPermissionService} resolves the
 * facts. Kept free of lookups so {@code ForumPermissionMatrixTest} can enumerate actor × forum × action.
 */
public final class ForumRules {

    private ForumRules() {
    }

    /**
     * Facts about the actor in one region.
     *
     * @param member      active member of the region / group
     * @param regionAdmin ambassador of the region / admin of the group (ambassador forum access)
     * @param moderator   ORGA, admin of the region's MODERATION group, else ambassador of the region or an ancestor
     */
    public record Actor(UUID userId, boolean orga, boolean member, boolean regionAdmin, boolean moderator,
                        boolean verified) {
    }

    /** Facts about one forum. */
    public record Forum(boolean ambassadorForum, boolean regionModerated, boolean hidingAllowed) {
    }

    public static boolean mayAccess(Actor actor, Forum forum) {
        if (actor.orga()) {
            return true;
        }
        return forum.ambassadorForum() ? actor.regionAdmin() : actor.member();
    }

    public static boolean mayStartThread(Actor actor, Forum forum) {
        return mayAccess(actor, forum);
    }

    /** New threads are active at once; otherwise they wait for a moderator. */
    public static boolean mayStartUnmoderatedThread(Actor actor, Forum forum) {
        if (!actor.verified()) {
            return false;
        }
        if (forum.ambassadorForum()) {
            return mayAccess(actor, forum);
        }
        return actor.moderator() || !forum.regionModerated();
    }

    public static boolean mayModerate(Actor actor, Forum forum) {
        return actor.orga() || actor.moderator() && mayAccess(actor, forum);
    }

    /** Inactive threads are visible to moderators and their authors only. */
    public static boolean maySeeThread(Actor actor, Forum forum, boolean active, UUID authorId) {
        return mayAccess(actor, forum) && (active || mayModerate(actor, forum) || actor.userId().equals(authorId));
    }

    public static boolean mayPostToThread(Actor actor, Forum forum, ThreadStatus status, boolean active) {
        if (!mayAccess(actor, forum)) {
            return false;
        }
        return status == ThreadStatus.OPEN && active || mayModerate(actor, forum);
    }

    public static boolean mayHidePosts(Actor actor, Forum forum) {
        return mayModerate(actor, forum) && forum.hidingAllowed();
    }

    public static boolean mayUnhidePosts(Actor actor, Forum forum) {
        return mayModerate(actor, forum);
    }

    public static boolean mayEditPost(Actor actor, Forum forum, UUID authorId) {
        return mayAccess(actor, forum) && actor.userId().equals(authorId);
    }

    public static boolean mayDeletePost(Actor actor, UUID authorId) {
        return actor.orga() || actor.userId().equals(authorId);
    }

    public static boolean mayDeleteThread(Actor actor, Forum forum, boolean active) {
        return actor.orga() || !active && mayModerate(actor, forum);
    }

    public static boolean mayRename(Actor actor, Forum forum, UUID authorId) {
        return mayModerate(actor, forum) || mayAccess(actor, forum) && actor.userId().equals(authorId);
    }

    public static boolean mayChangeSticky(Actor actor, Forum forum) {
        return mayModerate(actor, forum);
    }

    public static boolean mayChangeStatus(Actor actor, Forum forum) {
        return mayModerate(actor, forum);
    }

    public static boolean maySearchEveryForum(Actor actor) {
        return actor.orga();
    }
}
