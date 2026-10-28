package network.foodsharing.forum.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;
import network.foodsharing.forum.domain.ThreadStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** A36 matrix: actor × forum setup × action (upstream ForumPermissions). */
class ForumPermissionMatrixTest {

    static final UUID ME = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    enum Who {
        ORGA, MODERATOR, AMBASSADOR_NOT_MODERATOR, MEMBER_VERIFIED, MEMBER_UNVERIFIED, OUTSIDER
    }

    static ForumRules.Actor actor(Who who) {
        return switch (who) {
            case ORGA -> new ForumRules.Actor(ME, true, false, false, true, true);
            // admin of the MODERATION group (or ambassador without one), member and region admin
            case MODERATOR -> new ForumRules.Actor(ME, false, true, true, true, true);
            // ambassador, but the region has a MODERATION group with other admins
            case AMBASSADOR_NOT_MODERATOR -> new ForumRules.Actor(ME, false, true, true, false, true);
            case MEMBER_VERIFIED -> new ForumRules.Actor(ME, false, true, false, false, true);
            case MEMBER_UNVERIFIED -> new ForumRules.Actor(ME, false, true, false, false, false);
            case OUTSIDER -> new ForumRules.Actor(ME, false, false, false, false, true);
        };
    }

    static final ForumRules.Forum OPEN = new ForumRules.Forum(false, false, true);
    static final ForumRules.Forum MODERATED = new ForumRules.Forum(false, true, true);
    static final ForumRules.Forum NO_HIDING = new ForumRules.Forum(false, false, false);
    static final ForumRules.Forum AMBASSADORS = new ForumRules.Forum(true, true, true);

    /** who, forum, access, unmoderated start, moderate, hide, post to closed thread. */
    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of(Who.ORGA, OPEN, true, true, true, true, true),
                Arguments.of(Who.ORGA, AMBASSADORS, true, true, true, true, true),
                Arguments.of(Who.ORGA, NO_HIDING, true, true, true, false, true),
                Arguments.of(Who.MODERATOR, MODERATED, true, true, true, true, true),
                Arguments.of(Who.MODERATOR, NO_HIDING, true, true, true, false, true),
                Arguments.of(Who.MODERATOR, AMBASSADORS, true, true, true, true, true),
                Arguments.of(Who.AMBASSADOR_NOT_MODERATOR, MODERATED, true, false, false, false, false),
                Arguments.of(Who.AMBASSADOR_NOT_MODERATOR, AMBASSADORS, true, true, false, false, false),
                Arguments.of(Who.MEMBER_VERIFIED, OPEN, true, true, false, false, false),
                Arguments.of(Who.MEMBER_VERIFIED, MODERATED, true, false, false, false, false),
                Arguments.of(Who.MEMBER_VERIFIED, AMBASSADORS, false, false, false, false, false),
                Arguments.of(Who.MEMBER_UNVERIFIED, OPEN, true, false, false, false, false),
                Arguments.of(Who.OUTSIDER, OPEN, false, true, false, false, false),
                Arguments.of(Who.OUTSIDER, AMBASSADORS, false, false, false, false, false));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("matrix")
    void rules(Who who, ForumRules.Forum forum, boolean access, boolean unmoderated, boolean moderate, boolean hide,
               boolean postToClosed) {
        ForumRules.Actor actor = actor(who);
        assertThat(ForumRules.mayAccess(actor, forum)).as("access").isEqualTo(access);
        assertThat(ForumRules.mayStartThread(actor, forum)).as("start").isEqualTo(access);
        assertThat(ForumRules.mayStartUnmoderatedThread(actor, forum)).as("unmoderated").isEqualTo(unmoderated);
        assertThat(ForumRules.mayModerate(actor, forum)).as("moderate").isEqualTo(moderate);
        assertThat(ForumRules.mayHidePosts(actor, forum)).as("hide").isEqualTo(hide);
        assertThat(ForumRules.mayUnhidePosts(actor, forum)).as("unhide").isEqualTo(moderate);
        assertThat(ForumRules.mayChangeSticky(actor, forum)).as("sticky").isEqualTo(moderate);
        assertThat(ForumRules.mayPostToThread(actor, forum, ThreadStatus.OPEN, true)).as("post").isEqualTo(access);
        assertThat(ForumRules.mayPostToThread(actor, forum, ThreadStatus.CLOSED, true)).as("post closed")
                .isEqualTo(postToClosed);
        assertThat(ForumRules.mayPostToThread(actor, forum, ThreadStatus.OPEN, false)).as("post inactive")
                .isEqualTo(moderate);
        assertThat(ForumRules.maySeeThread(actor, forum, false, OTHER)).as("see inactive").isEqualTo(moderate);
        assertThat(ForumRules.maySeeThread(actor, forum, false, ME)).as("see own inactive").isEqualTo(access);
        assertThat(ForumRules.mayDeleteThread(actor, forum, false)).as("delete inactive").isEqualTo(moderate);
        assertThat(ForumRules.mayDeleteThread(actor, forum, true)).as("delete active").isEqualTo(who == Who.ORGA);
        assertThat(ForumRules.mayRename(actor, forum, OTHER)).as("rename other").isEqualTo(moderate);
        assertThat(ForumRules.mayRename(actor, forum, ME)).as("rename own").isEqualTo(access);
        assertThat(ForumRules.mayEditPost(actor, forum, ME)).as("edit own").isEqualTo(access);
        assertThat(ForumRules.mayEditPost(actor, forum, OTHER)).as("edit other").isFalse();
    }

    @Test
    void deletingPostsIsForAuthorsAndOrgaAndOnlyOrgaSearchesEverything() {
        assertThat(ForumRules.mayDeletePost(actor(Who.MEMBER_VERIFIED), ME)).isTrue();
        assertThat(ForumRules.mayDeletePost(actor(Who.MODERATOR), OTHER)).isFalse();
        assertThat(ForumRules.mayDeletePost(actor(Who.ORGA), OTHER)).isTrue();
        assertThat(ForumRules.maySearchEveryForum(actor(Who.ORGA))).isTrue();
        assertThat(ForumRules.maySearchEveryForum(actor(Who.MODERATOR))).isFalse();
    }
}
