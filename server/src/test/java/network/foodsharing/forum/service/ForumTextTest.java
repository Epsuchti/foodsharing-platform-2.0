package network.foodsharing.forum.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Mentions (FORUM_MENTION), search escaping and snippets (F5). */
class ForumTextTest {

    @Test
    void mentionsAreParsedFromTokensOnly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String body = "Hallo @[Anna](user:" + a + ") und @[Ben B.](user:" + b.toString().toUpperCase()
                + "), nochmals @[Anna](user:" + a + "). Kein Token: @Anna, [x](user:" + UUID.randomUUID() + ")";
        assertThat(ForumNotifier.mentionedUserIds(body)).containsExactly(a, b);
        assertThat(ForumNotifier.mentionedUserIds(null)).isEmpty();
    }

    @Test
    void likePatternsAreEscaped() {
        assertThat(ForumSearchService.escape("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }

    @Test
    void snippetsCutAroundTheFirstMatch() {
        String body = "a".repeat(100) + " Techniker kommt " + "b".repeat(100);
        String snippet = ForumSearchService.snippet(body, "techniker");
        assertThat(snippet).startsWith("…").endsWith("…").contains("Techniker kommt");
        assertThat(snippet.length()).isLessThanOrEqualTo(2 * ForumSearchService.SNIPPET_RADIUS + 9 + 2);
        assertThat(ForumSearchService.snippet("Kurz und gut", "kurz")).isEqualTo("Kurz und gut");
        assertThat(ForumSearchService.snippet("nichts", "x")).isNull();
    }
}
