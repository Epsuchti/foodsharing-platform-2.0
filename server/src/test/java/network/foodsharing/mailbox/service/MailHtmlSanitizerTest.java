package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A35 XSS corpus: what survives server-side sanitising of mail HTML. */
class MailHtmlSanitizerTest {

    private final MailHtmlSanitizer sanitizer = new MailHtmlSanitizer();

    @Test
    void removesScriptsHandlersFramesStylesAndJavascriptLinks() {
        String html = sanitizer.sanitize("<p onclick=\"x()\">Hi<script>alert(1)</script></p>"
                + "<iframe src=\"https://evil.example\"></iframe><style>p{color:red}</style>"
                + "<a href=\"javascript:alert(1)\">click</a><div style=\"background:url(x)\">s</div>"
                + "<img src=\"x\" onerror=\"alert(1)\"><svg onload=\"alert(1)\"></svg>");
        assertThat(html).doesNotContain("script", "onclick", "iframe", "<style", "javascript:", "style=", "onerror",
                "onload", "<svg", "<img").contains("<p>Hi</p>", ">click</a>", "<div>s</div>");
    }

    @Test
    void stripsRemoteImagesButKeepsInlineCidImagesAsAttachmentUrls() {
        String html = sanitizer.sanitize("<img src=\"https://tracker.example/p.gif\"><img src=\"cid:logo@x\" alt=\"Logo\">"
                + "<img src=\"cid:unknown\">", Map.of("logo@x", "/api/mailboxes/mails/1/attachments/2")::get);
        assertThat(html).doesNotContain("tracker", "unknown")
                .contains("<img src=\"/api/mailboxes/mails/1/attachments/2\" alt=\"Logo\">");
    }

    @Test
    void linksOpenInANewTabWithoutOpener() {
        String html = sanitizer.sanitize("<a href=\"https://foodsharing.example/\" target=\"_self\">x</a>"
                + "<a href=\"mailto:a@b.ch\">m</a>");
        assertThat(html).contains("href=\"https://foodsharing.example/\"", "target=\"_blank\"",
                "rel=\"noopener noreferrer\"", "href=\"mailto:a@b.ch\"").doesNotContain("_self");
    }

    @Test
    void keepsFormattingAndHandlesBlankInput() {
        assertThat(sanitizer.sanitize("<h2>T</h2><ul><li><b>a</b></li></ul><table><tr><td>c</td></tr></table>"))
                .contains("<h2>T</h2>", "<li><b>a</b></li>", "<td>c</td>");
        assertThat(sanitizer.sanitize("  ")).isNull();
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void plainTextKeepsLineBreaksAndDecodesEntities() {
        assertThat(sanitizer.toText("<p>Hallo &amp; willkommen</p><p>Zeile 1<br>Zeile 2</p>"))
                .isEqualTo("Hallo & willkommen\nZeile 1\nZeile 2");
        assertThat(MailHtmlSanitizer.textToHtml("a < b\nc")).isEqualTo("<p>a &lt; b<br>c</p>");
    }
}
