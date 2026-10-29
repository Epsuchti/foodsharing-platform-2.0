package network.foodsharing.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The {@code Content-Security-Policy} header (A58): no {@code 'unsafe-inline'} scripts. The SPA's HTML files
 * ({@code static/**}{@code /*.html}: shell, prerendered landing pages) are scanned once at startup; every inline
 * executable {@code <script>} is allowed by its SHA-256 hash and every inline event handler attribute (e.g. the
 * critical-CSS loader {@code onload="this.media='all'"}) by its hash with {@code 'unsafe-hashes'}. Styles keep
 * {@code 'unsafe-inline'} (Angular injects component styles at runtime).
 */
public final class ContentSecurityPolicy {

    private static final Logger log = LoggerFactory.getLogger(ContentSecurityPolicy.class);

    private static final Pattern SCRIPT = Pattern.compile("<script(\\s[^>]*)?>(.*?)</script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SRC = Pattern.compile("\\ssrc\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE = Pattern.compile("\\stype\\s*=\\s*[\"']?([^\"'\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLER = Pattern.compile("\\son[a-z]+\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> JS_TYPES = Set.of("text/javascript", "module", "application/javascript");

    private ContentSecurityPolicy() {
    }

    /** The header value for the SPA files found on the classpath. */
    public static String build(AppProperties.Security.Csp csp) {
        Set<String> scriptHashes = new TreeSet<>();
        Set<String> handlerHashes = new TreeSet<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:static/**/*.html")) {
                try (InputStream in = resource.getInputStream()) {
                    collect(new String(in.readAllBytes(), StandardCharsets.UTF_8), scriptHashes, handlerHashes);
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan the SPA HTML for inline scripts: {}", e.getMessage());
        }
        return policy(csp, scriptHashes, handlerHashes);
    }

    static String policy(AppProperties.Security.Csp csp, Set<String> scriptHashes, Set<String> handlerHashes) {
        StringBuilder script = new StringBuilder("'self'");
        scriptHashes.forEach(hash -> script.append(" '").append(hash).append('\''));
        if (!handlerHashes.isEmpty()) {
            script.append(" 'unsafe-hashes'");
            handlerHashes.forEach(hash -> script.append(" '").append(hash).append('\''));
        }
        String img = ("'self' data: blob: " + csp.getImgSrc()).strip();
        String connect = ("'self' " + csp.getConnectSrc()).strip();
        return "default-src 'self'; script-src " + script + "; style-src 'self' 'unsafe-inline'; img-src " + img
                + "; font-src 'self' data:; connect-src " + connect + "; worker-src 'self'; manifest-src 'self'"
                + "; media-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";
    }

    /** Adds the hashes of the inline scripts and event handlers of one HTML document. */
    static void collect(String html, Set<String> scriptHashes, Set<String> handlerHashes) {
        Matcher scripts = SCRIPT.matcher(html);
        while (scripts.find()) {
            String attributes = scripts.group(1) == null ? "" : scripts.group(1);
            String body = scripts.group(2);
            if (SRC.matcher(attributes).find() || body.isEmpty()) {
                continue;
            }
            Matcher type = TYPE.matcher(attributes);
            if (type.find() && !JS_TYPES.contains(type.group(1).toLowerCase(java.util.Locale.ROOT))) {
                continue; // e.g. application/json (ng-state) is never executed
            }
            scriptHashes.add(sha256(body));
        }
        String withoutScripts = SCRIPT.matcher(html).replaceAll("");
        Matcher handlers = HANDLER.matcher(withoutScripts);
        while (handlers.find()) {
            String value = handlers.group(2) != null ? handlers.group(2) : handlers.group(3);
            handlerHashes.add(sha256(unescape(value)));
        }
    }

    private static String unescape(String attribute) {
        return attribute.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256-" + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
