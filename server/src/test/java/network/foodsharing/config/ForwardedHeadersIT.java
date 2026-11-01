package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.regex.Pattern;
import network.foodsharing.common.web.HttpExchange;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;

/**
 * D17.2/A58 on a real Tomcat: {@code server.forward-headers-strategy=native} makes the {@link RemoteIpValve} read
 * {@code X-Forwarded-For} from the right and {@code X-Forwarded-Proto}, but only from the trusted proxies of
 * {@code server.tomcat.remoteip.internal-proxies}; the client IP of the rate limits is what it leaves behind.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ForwardedHeadersIT.EchoClientIp.class)
class ForwardedHeadersIT {

    static final String ECHO = "/__test/client-ip";

    @LocalServerPort
    int port;

    @Autowired
    WebServerApplicationContext context;

    /** Answers {@link #ECHO} with {@link HttpExchange#clientIp} before anything else runs. */
    @TestConfiguration(proxyBeanMethods = false)
    static class EchoClientIp {

        @Bean
        FilterRegistrationBean<Filter> echoClientIp() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                HttpServletRequest http = (HttpServletRequest) request;
                if (ECHO.equals(http.getRequestURI())) {
                    response.setContentType("text/plain");
                    response.getWriter().write(HttpExchange.clientIp(http) + " " + http.isSecure());
                    return;
                }
                chain.doFilter(request, response);
            });
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void fromATrustedProxyTheRightmostForwardedAddressIsTheClient() throws Exception {
        assertThat(get(ECHO).body()).isEqualTo("127.0.0.1 false");
        assertThat(get(ECHO, "X-Forwarded-For", "1.2.3.4, 5.6.7.8").body()).startsWith("5.6.7.8 ");
        // trusted proxies in the chain are skipped from the right
        assertThat(get(ECHO, "X-Forwarded-For", "1.2.3.4, 5.6.7.8, 10.0.0.3").body()).startsWith("5.6.7.8 ");
    }

    @Test
    void forwardedProtoHttpsStillTriggersHsts() throws Exception {
        assertThat(get(ECHO, "X-Forwarded-Proto", "https").body()).endsWith(" true");
        HttpResponse<String> https = get("/api/public/app-info", "X-Forwarded-Proto", "https");
        assertThat(https.statusCode()).isEqualTo(200);
        assertThat(https.headers().firstValue("Strict-Transport-Security"))
                .contains("max-age=31536000 ; includeSubDomains");
        assertThat(get("/api/public/app-info").headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    @Test
    void onlyTheConfiguredPrivateRangesAreTrustedProxies() {
        Valve[] valves = ((TomcatWebServer) context.getWebServer()).getTomcat().getEngine().getPipeline().getValves();
        RemoteIpValve valve = Arrays.stream(valves).filter(RemoteIpValve.class::isInstance)
                .map(RemoteIpValve.class::cast).findFirst().orElseThrow();
        Pattern trusted = Pattern.compile(valve.getInternalProxies());
        for (String proxy : new String[] {"127.0.0.1", "10.1.2.3", "192.168.0.10", "172.16.0.1", "172.31.255.254",
                "0:0:0:0:0:0:0:1", "::1", "fd12:3456:0:0:0:0:0:1", "fc00:0:0:0:0:0:0:1"}) {
            assertThat(trusted.matcher(proxy).matches()).as(proxy).isTrue();
        }
        // a direct client (or an unknown proxy) keeps its own remote address: its X-Forwarded-For is ignored
        for (String client : new String[] {"5.6.7.8", "203.0.113.7", "172.32.0.1", "172.15.0.1", "192.169.0.1",
                "11.0.0.1", "2001:db8:0:0:0:0:0:1", "fe80:0:0:0:0:0:0:1"}) {
            assertThat(trusted.matcher(client).matches()).as(client).isFalse();
        }
        assertThat(valve.getRemoteIpHeader()).isEqualToIgnoringCase("X-Forwarded-For");
        assertThat(valve.getProtocolHeader()).isEqualToIgnoringCase("X-Forwarded-Proto");
    }
}
