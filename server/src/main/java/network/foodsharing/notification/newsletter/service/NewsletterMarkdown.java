package network.foodsharing.notification.newsletter.service;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Newsletter body (N7): CommonMark → HTML with raw HTML escaped, then cleaned with jsoup's {@code basic} safelist
 * plus headings (links only http/https/mailto, {@code rel=nofollow}). The plain-text part keeps the markdown source.
 */
public final class NewsletterMarkdown {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();
    private static final Safelist SAFELIST = Safelist.basic().addTags("h1", "h2", "h3", "h4", "hr");

    private NewsletterMarkdown() {
    }

    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return Jsoup.clean(RENDERER.render(PARSER.parse(markdown)), SAFELIST);
    }
}
