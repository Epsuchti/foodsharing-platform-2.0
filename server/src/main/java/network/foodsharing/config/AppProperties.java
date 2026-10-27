package network.foodsharing.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration under {@code app.*}. Every key has a default in {@code application.properties}; profiles and
 * environment variables override them. Each module keeps its keys in its own nested class / properties block.
 */
@Getter
@Setter
@ConfigurationProperties("app")
public class AppProperties {

    /** Public host name, e.g. foodsharing.network (used for cookies, passkey rp-id defaults, mail links). */
    private String domain = "localhost";

    /** Absolute base URL of the frontend (no trailing slash), used for links in mails. */
    private String baseUrl = "http://localhost:4200";

    private final Auth auth = new Auth();
    private final Mail mail = new Mail();
    private final Clock clock = new Clock();
    private final Dev dev = new Dev();
    private final History history = new History();
    private final ClientErrors clientErrors = new ClientErrors();

    @Getter
    @Setter
    public static class Auth {
        private String cookieName = "fs_session";
        private boolean cookieSecure = false;
        private int sessionValidityDays = 365;
        /** last_seen_at / sliding expiry are written at most once per this many minutes. */
        private int sessionTouchIntervalMinutes = 5;
        /** Salt for IP hashes (sessions, rate limits, registration attempts). */
        private String ipSalt = "local-dev-salt";
        private int loginTokenValidityMinutes = 30;
        private int maxLoginRequestsPerEmail = 3;
        private int maxLoginRequestsPerIp = 10;
        private int loginRequestWindowMinutes = 15;
        /** HMAC key for short-lived signed cookies (Google login return URL). */
        private String signingSecret = "local-dev-signing-secret";
        /** In-app path after a login without (valid) return URL. */
        private String defaultReturnPath = "/";
        private final Google google = new Google();
        private final MagicLink magicLink = new MagicLink();
        private final Passkeys passkeys = new Passkeys();

        @Getter
        @Setter
        public static class Google {
            private boolean enabled = true;
            /** Expected id_token issuer (validated by the OIDC user service; no discovery at startup). */
            private String issuer = "https://accounts.google.com";
        }

        @Getter
        @Setter
        public static class MagicLink {
            private boolean enabled = true;
            /** Frontend route the mail links to: {@code <base-url><verify-path>?token=…[&returnUrl=…]}. */
            private String verifyPath = "/login/verify";
        }

        @Getter
        @Setter
        public static class Passkeys {
            private boolean enabled = true;
            /** Must equal the cookie host (localhost in local/e2e). */
            private String rpId = "localhost";
            private String rpName = "foodsharing";
            /** Origins allowed in WebAuthn client data; empty = {@code app.base-url}. */
            private List<String> allowedOrigins = new ArrayList<>();
        }
    }

    @Getter
    @Setter
    public static class Mail {
        private String from = "noreply@foodsharing.network";
        /** smtp (default) or capture (e2e: mails are kept in memory and served by /api/dev/mails). */
        private String transport = "smtp";
        /** Mails sent per outbox job run. */
        private int outboxBatchSize = 20;
        /** After this many failed attempts a mail is marked FAILED. */
        private int outboxMaxAttempts = 8;
        /** Retry delay = base × 2^(attempts − 1). */
        private int outboxBackoffBaseMinutes = 1;
    }

    @Getter
    @Setter
    public static class Clock {
        /** When set, the application clock is fixed at this instant (e2e, screenshots). */
        private Instant fixed;
    }

    @Getter
    @Setter
    public static class Dev {
        /** True in local/e2e: dev user switcher, gallery, /api/dev/**. */
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class History {
        private boolean retentionEnabled = true;
        private String retentionCron = "0 30 3 * * *";
        /** Maximum rows of a CSV export. */
        private int exportMaxRows = 10_000;
    }

    @Getter
    @Setter
    public static class ClientErrors {
        private int maxPerMinutePerIp = 30;
    }
}
