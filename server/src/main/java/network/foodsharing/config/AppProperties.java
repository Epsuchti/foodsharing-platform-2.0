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
    // --- milestone 4 ---
    private final Notifications notifications = new Notifications();
    private final Push push = new Push();
    private final Telegram telegram = new Telegram();
    private final Automation automation = new Automation();
    private final Newsletter newsletter = new Newsletter();
    private final Integrations integrations = new Integrations();
    private final Realtime realtime = new Realtime();
    private final Messaging messaging = new Messaging();
    private final Uploads uploads = new Uploads();
    private final Mailbox mailbox = new Mailbox();
    private final Forum forum = new Forum();
    private final Polls polls = new Polls();

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
        /** M4 A27: per-recipient limits; mails beyond them are deferred, never dropped. */
        private final RateLimit rateLimit = new RateLimit();

        @Getter
        @Setter
        public static class RateLimit {
            private int perUserPerHour = 20;
            private int perTemplatePerUserPerDay = 5;
            /** Same template + identifier within this window is skipped (MAIL_SUPPRESSED). */
            private int duplicateWindowMinutes = 10;
        }
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
        /** ShedLock upper bound for a running job (A25). */
        private String defaultLockAtMostFor = "PT30M";
        private String storeMaintenanceCron = "0 30 2 * * *";
        private String cleanupCron = "0 0 4 * * *";
        private String auditRetentionCron = "0 15 3 * * *";
        /** job_run rows older than this are deleted by the cleanup job. */
        private int runRetentionDays = 90;
    }

    /** Bells and channel fan-out (A23). */
    @Getter
    @Setter
    public static class Notifications {
        /** How often the client polls the unread count. */
        private int pollSeconds = 60;
    }

    /** Web push (A23). */
    @Getter
    @Setter
    public static class Push {
        /** log (local/e2e/test: pushes are captured for /api/dev/notifications) or webpush (prod). */
        private String mode = "webpush";
        private int maxSubscriptionsPerUser = 10;
        /** Subscriptions with this many consecutive failures are removed by the cleanup job. */
        private int maxFailures = 5;
        private final Vapid vapid = new Vapid();

        @Getter
        @Setter
        public static class Vapid {
            /** Base64url public key (env PUSH_VAPID_PUBLIC). */
            private String publicKey = "";
            /** Base64url private key (env PUSH_VAPID_PRIVATE). */
            private String privateKey = "";
            /** mailto: or https: contact (env PUSH_VAPID_SUBJECT). */
            private String subject = "mailto:it@foodsharing.network";
        }
    }

    /** Telegram bot (A28). */
    @Getter
    @Setter
    public static class Telegram {
        /** log (local/e2e/test: messages are captured) or live. */
        private String mode = "live";
        /** Bot token (env TELEGRAM_BOT_TOKEN); empty in live mode = Telegram not configured. */
        private String botToken = "";
        private String botName = "foodsharing_bot";
        /** Path secret of POST /api/public/telegram/webhook/{secret}; empty = webhook off (polling). */
        private String webhookSecret = "";
        private String apiUrl = "https://api.telegram.org";
        private String pollInterval = "PT30S";
        private int linkTokenMinutes = 15;
        private int timeoutSeconds = 10;
    }

    /** Store and region automations (A24). */
    @Getter
    @Setter
    public static class Automation {
        private boolean enabled = true;
        /** Global override: every automation runs as dry run. */
        private boolean dryRun = false;
        private int cleaningBackCheckMonths = 12;
        private int minimumFreeCleaningSlots = 4;
        private int maxRequestApprovalsPerRun = 50;
        private int maxAdvertisementsPerRun = 50;
        private String evaluateCron = "0 */5 * * * *";
        private String advertsCron = "0 */5 * * * *";
        private String signoutCron = "0 0 * * * *";
        /** Local time after which the daily sign-out check runs (store time zone). */
        private String signoutDailyTime = "03:00";
        private int signoutRetentionDays = 180;
        private int auditRetentionDays = 7;
        /** static (built-in Swiss table) or nager (date.nager.at with static fallback). */
        private String holidayProvider = "static";
        private String nagerUrl = "https://date.nager.at";
    }

    /** Newsletters (N7). */
    @Getter
    @Setter
    public static class Newsletter {
        private int batchSize = 50;
        private int mailsPerMinute = 100;
        private String sendCron = "0 * * * * *";
        /** HMAC key of the unsubscribe tokens (env NEWSLETTER_SECRET in prod). */
        private String secret = "local-newsletter-secret";
    }

    /** Circuit breakers of external integrations (A26). */
    @Getter
    @Setter
    public static class Integrations {
        /** While a breaker is open, the next probe is attempted after this interval. */
        private String probeInterval = "PT5M";
        /** How long outages are remembered (linked-store grace extension). */
        private int outageHistoryDays = 3;
    }

    /** Realtime STOMP endpoint /ws (A32). */
    @Getter
    @Setter
    public static class Realtime {
        private boolean enabled = true;
        private long heartbeatMs = 10000;
    }

    /** Conversations (A33). */
    @Getter
    @Setter
    public static class Messaging {
        private int maxMessageLength = 10000;
        /** At most one NEW_MESSAGE mail per conversation and member within this many minutes while unread. */
        private int mailDigestMinutes = 30;
        private String syncCron = "0 15 * * * *";
    }

    /** Minimal uploads (A34). */
    @Getter
    @Setter
    public static class Uploads {
        private String dir = "./data/uploads";
        private int maxSizeMb = 20;
        private List<String> allowedTypes = new ArrayList<>();
        private int unclaimedTtlHours = 24;
    }

    /** Mailboxes (A35). */
    @Getter
    @Setter
    public static class Mailbox {
        /** Mail domain of mailbox addresses (env MAILBOX_DOMAIN); empty = compose answers 503. */
        private String domain = "";
        /** log (fetches nothing) or imap. */
        private String inbound = "log";
        private final Imap imap = new Imap();
        /** Path secret of POST /api/public/mailbox/inbound/{secret}; empty = webhook off. */
        private String webhookSecret = "";
        private String fetchCron = "0 * * * * *";
        private List<String> reservedNames = new ArrayList<>();
        private int maxMailsPerHourPerUser = 50;
        private int maxAttachmentTotalMb = 20;

        @Getter
        @Setter
        public static class Imap {
            private String host = "";
            private int port = 993;
            private String user = "";
            private String password = "";
            private String folder = "INBOX";
            private boolean ssl = true;
        }
    }

    /** Forum (A36). */
    @Getter
    @Setter
    public static class Forum {
        private int searchMinLength = 2;
    }

    /** Polls (A36). */
    @Getter
    @Setter
    public static class Polls {
        private String minLeadTime = "PT1H";
        private int endNotificationMinHours = 30;
        private String notifyCron = "0 */15 * * * *";
    }
}
