package network.foodsharing.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.security.Permission;
import network.foodsharing.security.WorkingGroupFunction;
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
    // --- milestone 2 ---
    private final Permissions permissions = new Permissions();
    private final Region region = new Region();
    private final Geocoding geocoding = new Geocoding();
    private final Quiz quiz = new Quiz();
    private final Intropickup intropickup = new Intropickup();
    private final Store store = new Store();
    private final Pickup pickup = new Pickup();
    private final Verification verification = new Verification();
    private final Wallet wallet = new Wallet();
    private final Onboarding onboarding = new Onboarding();
    private final Jobs jobs = new Jobs();

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

    @Getter
    @Setter
    public static class Permissions {
        /**
         * Region permissions granted to every member of a region's function group (plan §9, A10). Defaults:
         * FSMANAGEMENT → CAN_MANAGE_USERS, WELCOME → CAN_GIVE_INTRO_PICKUPS, STORES_COORDINATION → the automation
         * permissions, VOTING → CAN_MANAGE_SURVEYS.
         */
        private Map<WorkingGroupFunction, List<Permission>> functionGrants = defaultFunctionGrants();

        public static Map<WorkingGroupFunction, List<Permission>> defaultFunctionGrants() {
            Map<WorkingGroupFunction, List<Permission>> grants = new EnumMap<>(WorkingGroupFunction.class);
            grants.put(WorkingGroupFunction.FSMANAGEMENT, List.of(Permission.CAN_MANAGE_USERS));
            grants.put(WorkingGroupFunction.WELCOME, List.of(Permission.CAN_GIVE_INTRO_PICKUPS));
            grants.put(WorkingGroupFunction.STORES_COORDINATION, List.of(Permission.CAN_USE_AUTOMATIONS,
                    Permission.CAN_SEE_USER_PICKUP_COUNT_GROUPING, Permission.CAN_SEE_ALL_AUTOMATION_DECISIONS));
            grants.put(WorkingGroupFunction.VOTING, List.of(Permission.CAN_MANAGE_SURVEYS));
            return grants;
        }
    }

    @Getter
    @Setter
    public static class Region {
        private String defaultTimeZone = "Europe/Zurich";
        private String maintenanceCron = "0 15 3 * * *";
        private String statisticsCron = "0 45 3 * * *";
        /** {slug} is replaced by the region / group slug. */
        private String conferenceUrlTemplate = "https://meet.jit.si/foodsharing-{slug}";
        /** Region suggestions by distance only within this radius. */
        private double suggestionRadiusKm = 50;
        /** Group mails per user and hour. */
        private int groupMailsPerHour = 5;
    }

    @Getter
    @Setter
    public static class Geocoding {
        /** photon (prod) or static (local/e2e/test: db/seed/region/geocoding.csv). */
        private String provider = "photon";
        private String photonUrl = "https://photon.komoot.io";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 3000;
        private int cacheHours = 24;
        /** Consecutive failures that open the circuit breaker, and how long it stays open. */
        private int failureThreshold = 5;
        private int openSeconds = 60;
    }

    @Getter
    @Setter
    public static class Quiz {
        private int networkBufferSeconds = 5;
        private int hygieneValidityDays = 365;
        private int hygieneWarningDays = 90;
        private int roleQuizWaitDaysThirdTry = 30;
    }

    @Getter
    @Setter
    public static class Intropickup {
        private int maxSlotCount = 3;
        private int importMaxRows = 5000;
        private int gapHorizonDays = 30;
        private int stalledAfterDays = 30;
        private List<String> icalAllowedHosts = new ArrayList<>(List.of("foodsharing.network", "foodsharing.de"));
    }

    @Getter
    @Setter
    public static class Store {
        private int maxManagers = 3;
        private int defaultPrefetchDays = 14;
        private int maxSlotsPerPickup = 50;
        private int wallDeleteAfterDays = 30;
    }

    @Getter
    @Setter
    public static class Pickup {
        private int reminderHoursBefore = 24;
        private String reminderCron = "0 0 * * * *";
        private int freeSlotStatusDays = 5;
    }

    @Getter
    @Setter
    public static class Verification {
        private int passportValidityYears = 3;
    }

    @Getter
    @Setter
    public static class Wallet {
        private final Apple apple = new Apple();
        private final Google google = new Google();

        @Getter
        @Setter
        public static class Apple {
            private boolean enabled = false;
            private String passTypeId;
            private String teamId;
            /** Path of the PKCS#12 signing certificate. */
            private String certificateP12;
            private String certificatePassword;
            /** Path of the Apple WWDR intermediate certificate. */
            private String wwdrCertificate;
        }

        @Getter
        @Setter
        public static class Google {
            private boolean enabled = false;
            private String issuerId;
            private String classId;
            /** Path of the service account JSON key. */
            private String serviceAccountJson;
        }
    }

    @Getter
    @Setter
    public static class Onboarding {
        private String nudgeCron = "0 0 9 * * *";
        private int stalledAfterDays = 30;
    }

    @Getter
    @Setter
    public static class Jobs {
        /** How long finished job results stay available (ProgressTracker). */
        private int resultTtlMinutes = 60;
    }
}
