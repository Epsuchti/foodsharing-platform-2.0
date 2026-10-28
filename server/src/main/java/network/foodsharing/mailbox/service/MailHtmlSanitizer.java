package network.foodsharing.mailbox.service;

import java.util.function.Function;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Server-side HTML sanitising of mail bodies (A35): jsoup {@link Safelist#relaxed()} without remote images (only
 * inline {@code cid:} images survive, rewritten to attachment URLs), no {@code style}, scripts, iframes or event
 * handlers, {@code javascript:} links removed, every link {@code rel="noopener noreferrer" target="_blank"}.
 */
@Component
public class MailHtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .removeTags("img")
            .addTags("img", "font", "center", "hr")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("img", "src", "cid")
            .addAttributes("a", "target")
            .addAttributes(":all", "align", "dir")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    /**
     * @param html     untrusted HTML (may be null)
     * @param cidToUrl maps a Content-ID (without {@code cid:} and angle brackets) to an attachment URL, null = drop
     * @return sanitised HTML, null for null/blank input
     */
    public String sanitize(String html, Function<String, String> cidToUrl) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = Jsoup.clean(html, "", SAFELIST, OUTPUT);
        Document doc = Jsoup.parseBodyFragment(clean);
        doc.outputSettings(OUTPUT);
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            String url = null;
            if (src.regionMatches(true, 0, "cid:", 0, 4) && cidToUrl != null) {
                url = cidToUrl.apply(stripAngles(src.substring(4)));
            }
            if (url == null) {
                img.remove();
            } else {
                img.attr("src", url);
            }
        }
        for (Element link : doc.select("a")) {
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer");
        }
        return doc.body().html();
    }

    public String sanitize(String html) {
        return sanitize(html, null);
    }

    /** Plain text of an HTML body (line breaks for br/p/div/li), e.g. the text part of a composed mail. */
    public String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(OUTPUT);
        doc.select("br").before("\\n");
        doc.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, blockquote").after("\\n");
        String text = Jsoup.clean(doc.body().html(), "", Safelist.none(), OUTPUT).replace("\\n", "\n");
        return Parser.unescapeEntities(text, false).replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    /** Escapes plain text as HTML paragraphs (incoming text-only mails shown as HTML, quoting). */
    public static String textToHtml(String text) {
        if (text == null) {
            return "";
        }
        return "<p>" + org.jsoup.nodes.Entities.escape(text).replace("\r\n", "\n").replace("\n", "<br>") + "</p>";
    }

    static String stripAngles(String value) {
        String v = value.strip();
        if (v.startsWith("<") && v.endsWith(">")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }
}
