package network.foodsharing.notification.newsletter.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Newsletter markdown is rendered and sanitised (N7). */
class NewsletterMarkdownTest {

    @Test
    void rendersMarkdown() {
        assertThat(NewsletterMarkdown.toHtml("## Titel\n\n**fett** [Link](https://foodsharing.network)"))
                .contains("<h2>Titel</h2>").contains("<strong>fett</strong>")
                .contains("href=\"https://foodsharing.network\"").contains("rel=\"nofollow\"");
    }

    @Test
    void stripsScriptsAndDangerousLinks() {
        String html = NewsletterMarkdown.toHtml("<script>alert(1)</script>\n\n[x](javascript:alert(1)) "
                + "<img src=x onerror=alert(1)>");
        assertThat(html).doesNotContain("<script").doesNotContain("javascript:").doesNotContain("<img")
                .doesNotContain("onerror=\"");
        assertThat(NewsletterMarkdown.toHtml(" ")).isEmpty();
    }
}
