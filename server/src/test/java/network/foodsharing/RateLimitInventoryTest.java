package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import network.foodsharing.common.metrics.OperationsMetrics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * D17.2 rate-limit inventory (docs/security-review.md "Rate limits"): every operation that documents 429 is backed by
 * a known limiter, every limiter is a pre-registered metric, and every anonymous operation is limited, either by the
 * per-IP filter on {@code /api/public/**} or by its own limiter listed here. A new 429 or a new anonymous endpoint
 * fails this test until it is added to the inventory.
 */
class RateLimitInventoryTest {

    private static final Path PATHS = Path.of("src/main/resources/openapi/paths");

    /** operationId → limiter (metric tag of fs_rate_limit_rejections_total, or a cooldown documented in the review). */
    static final Map<String, String> LIMITED_OPERATIONS = Map.ofEntries(
            Map.entry("requestMagicLink", "login-ip"),
            Map.entry("getSession", "login-ip"),
            Map.entry("sendMessage", "messages"),
            Map.entry("requestBasket", "basket-requests"),
            Map.entry("sendGroupMail", "group-mails"),
            Map.entry("createSupportTicket", "support"),
            Map.entry("sendMail", "mailbox-hourly"),
            Map.entry("exportMyData", "data-export-cooldown"));

    /** Limits that are cooldowns / quotas in the database rather than in-memory sliding windows. */
    static final Set<String> NON_METRIC_LIMITERS = Set.of("mailbox-hourly", "data-export-cooldown");

    /**
     * Anonymous operations outside /api/public/** with the reason why no extra limit is needed (each is either
     * protected by a secret in the request or limited elsewhere).
     */
    static final Map<String, String> ANONYMOUS_OUTSIDE_PUBLIC = Map.ofEntries(
            Map.entry("logout", "only ends the caller's own session; nothing to enumerate or flood"),
            Map.entry("verifyMagicLink", "256-bit single-use token with a short expiry; guessing is infeasible, "
                    + "failures are counted (fs_login_failures_total) and alerted"));

    /** /api/public/** operations the per-IP filter skips (PublicApiRateLimitFilter.WEBHOOKS). */
    static final Set<String> PUBLIC_WEBHOOKS = Set.of("/api/public/mailbox/inbound/{secret}",
            "/api/public/telegram/webhook/{secret}");

    record Operation(String file, String path, String method, String id, boolean anonymous, boolean documents429) {
    }

    private static final List<Operation> OPERATIONS = new ArrayList<>();

    @BeforeAll
    static void load() throws IOException {
        if (!OPERATIONS.isEmpty()) {
            return;
        }
        try (Stream<Path> files = Files.list(PATHS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    Object loaded = new Yaml().load(in);
                    if (!(loaded instanceof Map<?, ?> paths)) {
                        continue;
                    }
                    paths.forEach((path, item) -> {
                        if (!(item instanceof Map<?, ?> methods)) {
                            return;
                        }
                        methods.forEach((method, op) -> {
                            if (!(op instanceof Map<?, ?> operation) || Boolean.TRUE.equals(operation.get("x-dev-only"))
                                    || !Set.of("get", "post", "put", "delete", "patch").contains(method)) {
                                return;
                            }
                            Object security = operation.get("security");
                            boolean anonymous = security instanceof List<?> list && list.isEmpty();
                            Map<?, ?> responses = (Map<?, ?>) operation.get("responses");
                            boolean has429 = responses != null
                                    && responses.keySet().stream().anyMatch(k -> "429".equals(String.valueOf(k)));
                            OPERATIONS.add(new Operation(file.getFileName().toString(), String.valueOf(path),
                                    String.valueOf(method), String.valueOf(operation.get("operationId")), anonymous,
                                    has429));
                        });
                    });
                }
            }
        }
    }

    private static boolean publicApi(Operation op) {
        return op.path().startsWith("/api/public/");
    }

    @Test
    void every429IsBackedByAKnownLimiter() {
        Set<String> unbacked = new TreeSet<>();
        for (Operation op : OPERATIONS) {
            if (op.documents429() && !publicApi(op) && !LIMITED_OPERATIONS.containsKey(op.id())) {
                unbacked.add(op.method() + " " + op.path() + " (" + op.id() + ")");
            }
        }
        assertThat(unbacked).as("operations documenting 429 without a limiter in the inventory").isEmpty();
    }

    @Test
    void inventoryMatchesTheContractAndTheMetrics() {
        Set<String> ids = new TreeSet<>();
        OPERATIONS.stream().filter(Operation::documents429).forEach(op -> ids.add(op.id()));
        assertThat(ids).as("inventory entries must document 429").containsAll(LIMITED_OPERATIONS.keySet());
        for (String limiter : LIMITED_OPERATIONS.values()) {
            if (!NON_METRIC_LIMITERS.contains(limiter)) {
                assertThat(OperationsMetrics.LIMITERS).as("limiter metric").contains(limiter);
            }
        }
        assertThat(OperationsMetrics.LIMITERS).contains("public-api");
    }

    @Test
    void everyPublicOperationDocuments429() {
        Set<String> missing = new TreeSet<>();
        OPERATIONS.stream().filter(op -> publicApi(op) && !op.documents429() && !PUBLIC_WEBHOOKS.contains(op.path()))
                .forEach(op -> missing.add(op.method() + " " + op.path() + " (" + op.file() + ")"));
        assertThat(missing).as("/api/public/** is rate limited per IP: document 429").isEmpty();
    }

    @Test
    void everyAnonymousOperationIsLimited() {
        Set<String> unlimited = new TreeSet<>();
        OPERATIONS.stream()
                .filter(op -> op.anonymous() && !publicApi(op) && !LIMITED_OPERATIONS.containsKey(op.id())
                        && !ANONYMOUS_OUTSIDE_PUBLIC.containsKey(op.id()))
                .forEach(op -> unlimited.add(op.method() + " " + op.path() + " (" + op.id() + ")"));
        assertThat(unlimited).as("anonymous operations without a limit or a documented reason").isEmpty();
    }
}
