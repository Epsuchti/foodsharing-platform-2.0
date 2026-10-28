package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.content.service.BlogRules;
import network.foodsharing.content.service.BlogRules.Actor;
import network.foodsharing.content.service.BlogRules.Post;
import org.junit.jupiter.api.Test;

/** Upstream BlogPermissions: editors administrate, ambassadors write for their regions, authors edit drafts. */
class BlogRulesTest {

    private static final UUID REGION = UUID.randomUUID();
    private static final UUID OTHER_REGION = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();

    private final Actor editor = new Actor(ME, true, Set.of());
    private final Actor ambassador = new Actor(ME, false, Set.of(REGION));
    private final Actor member = new Actor(ME, false, Set.of());

    @Test
    void createNeedsEditorOrAmbassadorOfTheRegion() {
        assertThat(BlogRules.mayCreate(editor, null)).isTrue();
        assertThat(BlogRules.mayCreate(ambassador, REGION)).isTrue();
        assertThat(BlogRules.mayCreate(ambassador, OTHER_REGION)).isFalse();
        assertThat(BlogRules.mayCreate(ambassador, null)).isFalse();
        assertThat(BlogRules.mayCreate(member, REGION)).isFalse();
    }

    @Test
    void editAndDelete() {
        Post regional = new Post(REGION, UUID.randomUUID(), true);
        Post foreign = new Post(OTHER_REGION, UUID.randomUUID(), false);
        Post ownDraft = new Post(OTHER_REGION, ME, false);
        Post ownPublished = new Post(null, ME, true);
        assertThat(BlogRules.mayEdit(editor, foreign)).isTrue();
        assertThat(BlogRules.mayEdit(ambassador, regional)).isTrue();
        assertThat(BlogRules.mayEdit(ambassador, foreign)).isFalse();
        assertThat(BlogRules.mayEdit(member, ownDraft)).isTrue();
        assertThat(BlogRules.mayEdit(member, ownPublished)).isFalse();
        assertThat(BlogRules.mayDelete(member, foreign)).isFalse();
        assertThat(BlogRules.maySeeDraft(ambassador, regional)).isTrue();
    }

    @Test
    void onlyEditorsPublishAndOthersChangesNeedACheck() {
        assertThat(BlogRules.mayPublish(editor)).isTrue();
        assertThat(BlogRules.mayPublish(ambassador)).isFalse();
        assertThat(BlogRules.changeNeedsCheck(ambassador, new Post(REGION, ME, true))).isTrue();
        assertThat(BlogRules.changeNeedsCheck(ambassador, new Post(REGION, ME, false))).isFalse();
        assertThat(BlogRules.changeNeedsCheck(editor, new Post(REGION, ME, true))).isFalse();
    }
}
