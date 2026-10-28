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

    @Test
    void sha256MatchesTheBrowserFormat() {
        // echo -n "alert(1)" | openssl dgst -sha256 -binary | base64
        assertThat(ContentSecurityPolicy.sha256("alert(1)"))
                .isEqualTo("sha256-bhHHL3z2vDgxUt0W3dWQOrprscmda2Y5pLsLg4GF+pI=");
    }
}
