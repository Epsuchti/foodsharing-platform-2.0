package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ContentSecurityPolicyTest {

    @Test
    void inlineScriptsAndHandlersAreHashedJsonAndExternalScriptsAreNot() {
        String html = "<html><head><script>try { x(); } catch (e) {}</script>"
                + "<script src=\"main.js\" type=\"module\"></script>"
                + "<script id=\"ng-state\" type=\"application/json\">{\"a\":1}</script>"
                + "<script type=\"module\">import('./x.js')</script>"
                + "<link rel=\"stylesheet\" href=\"styles.css\" media=\"print\" onload=\"this.media='all'\">"
                + "</head><body></body></html>";
        Set<String> scripts = new TreeSet<>();
        Set<String> handlers = new TreeSet<>();
        ContentSecurityPolicy.collect(html, scripts, handlers);

        assertThat(scripts).containsExactlyInAnyOrder(ContentSecurityPolicy.sha256("try { x(); } catch (e) {}"),
                ContentSecurityPolicy.sha256("import('./x.js')"));
        assertThat(handlers).containsExactly(ContentSecurityPolicy.sha256("this.media='all'"));
    }

    @Test
    void policyNeverAllowsUnsafeInlineScripts() {
        AppProperties.Security.Csp csp = new AppProperties.Security.Csp();
        String policy = ContentSecurityPolicy.policy(csp, Set.of("sha256-abc"), Set.of("sha256-def"));
        String scriptSrc = policy.substring(policy.indexOf("script-src"), policy.indexOf(';', policy.indexOf("script-src")));

        assertThat(scriptSrc).isEqualTo("script-src 'self' 'sha256-abc' 'unsafe-hashes' 'sha256-def'");
        assertThat(policy).contains("object-src 'none'", "base-uri 'self'", "frame-ancestors 'self'",
                "img-src 'self' data: blob: https://tile.openstreetmap.org", "connect-src 'self';");
        assertThat(ContentSecurityPolicy.policy(csp, Set.of(), Set.of())).contains("script-src 'self';")
                .doesNotContain("unsafe-hashes").doesNotContain("unsafe-eval");
    }

    /**
     * D17.2 strictness: every directive that falls back to nothing must be set, no source list contains a wildcard,
     * a plain scheme (http:, https:, ws:) or 'unsafe-eval', and only style-src may use 'unsafe-inline' (Angular's
     * component styles, accepted in docs/security-review.md).
     */
    @Test
    void frameSrcIsSelfPlusTheHttpsOriginsOfTheConfiguredAndDonationUrls() {
        AppProperties.Security.Csp csp = new AppProperties.Security.Csp();
        assertThat(ContentSecurityPolicy.policy(csp, Set.of(), Set.of())).contains("; frame-src 'self';");

        csp.setFrameSrc("https://widgets.example.org  http://insecure.example.org");
        String policy = ContentSecurityPolicy.policy(csp, Set.of(), Set.of(), java.util.List.of(
                "https://Spenden.Example.ch/form?id=1&lang=de", "https://donate.example.com:8443/x",
                "https://spenden.example.ch/other", "http://plain.example.ch/x", "javascript:alert(1)",
                "data:text/html,x", "https://*.example.ch/", "//no-scheme.example.ch/", "not a url", ""));
        assertThat(policy).contains("; frame-src 'self' https://widgets.example.org https://donate.example.com:8443 "
                + "https://spenden.example.ch;");
    }

    @Test
    void httpsOriginAcceptsOnlyHttpsHosts() {
        assertThat(ContentSecurityPolicy.httpsOrigin("https://raisenow.example.com/a?b=c#d"))
                .isEqualTo("https://raisenow.example.com");
        assertThat(ContentSecurityPolicy.httpsOrigin("HTTPS://Host.example:444")).isEqualTo("https://host.example:444");
        assertThat(ContentSecurityPolicy.httpsOrigin("http://host.example")).isNull();
        assertThat(ContentSecurityPolicy.httpsOrigin("https://*.host.example")).isNull();
        assertThat(ContentSecurityPolicy.httpsOrigin("https://host.example;script-src *")).isNull();
        assertThat(ContentSecurityPolicy.httpsOrigin("https://host example")).isNull();
        assertThat(ContentSecurityPolicy.httpsOrigin(null)).isNull();
    }

    @Test
    void policyIsStrict() {
        AppProperties.Security.Csp csp = new AppProperties.Security.Csp();
        csp.setFrameSrc("* https: http://x.example 'unsafe-inline'");
        String policy = ContentSecurityPolicy.policy(csp, Set.of("sha256-abc"), Set.of(), java.util.List.of(
                "https://donate.example.org/form", "http://plain.example.org", "https://*.example.org",
                "https://x.example.org;script-src *", "*"));
        java.util.Map<String, String> directives = new java.util.LinkedHashMap<>();
        for (String part : policy.split(";")) {
            String[] tokens = part.strip().split("\\s+", 2);
            assertThat(directives.put(tokens[0], tokens.length > 1 ? tokens[1] : "")).as("duplicate " + tokens[0])
                    .isNull();
        }
        assertThat(directives).containsEntry("default-src", "'self'").containsEntry("object-src", "'none'")
                .containsEntry("base-uri", "'self'").containsEntry("form-action", "'self'")
                .containsEntry("frame-ancestors", "'self'")
                .containsEntry("frame-src", "'self' https://donate.example.org")
                .containsKeys("script-src", "connect-src", "worker-src");
        directives.forEach((name, sources) -> {
            for (String source : sources.split("\\s+")) {
                assertThat(source).as(name).isNotIn("*", "http:", "https:", "ws:", "wss:", "'unsafe-eval'",
                        "'strict-dynamic'");
                if (!name.equals("style-src")) {
                    assertThat(source).as(name).isNotEqualTo("'unsafe-inline'");
                }
                assertThat(source).as(name).doesNotStartWith("http://");
            }
        });
    }

    @Test
    void sha256MatchesTheBrowserFormat() {
        // echo -n "alert(1)" | openssl dgst -sha256 -binary | base64
        assertThat(ContentSecurityPolicy.sha256("alert(1)"))
                .isEqualTo("sha256-bhHHL3z2vDgxUt0W3dWQOrprscmda2Y5pLsLg4GF+pI=");
    }
}
