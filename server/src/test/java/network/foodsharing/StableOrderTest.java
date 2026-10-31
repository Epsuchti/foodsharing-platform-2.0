package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Stable order (resume item 18, decision 137): every ordered repository query ends with a unique key, so rows with the
 * same timestamp (or name, or date) never come back in a random order and a page boundary never moves between calls.
 * The key is the id, except for the few order keys in {@link #UNIQUE_ORDER_KEYS} that are unique by a database
 * constraint.
 */
class StableOrderTest {

    /**
     * Order keys that are already unique together with the query's equality filter (the named unique constraint),
     * so no id tiebreaker is needed. Keyed by {@code Repository#method}.
     */
    private static final Map<String, String> UNIQUE_ORDER_KEYS = Map.ofEntries(
            Map.entry("ConversationMessageRepository#findByConversationIdOrderBySeqDesc", "message(conversation_id, seq)"),
            Map.entry("ConversationMessageRepository#findByConversationIdAndSeqLessThanOrderBySeqDesc",
                    "message(conversation_id, seq)"),
            Map.entry("ConversationMessageRepository#findByConversationIdAndSeqGreaterThanOrderBySeqAsc",
                    "message(conversation_id, seq)"),
            Map.entry("OpenSlotAdvertRepository#findByStoreIdOrderByAdvertNumber", "open_slot_advert(store_id, advert_number)"),
            Map.entry("OpenSlotAdvertRepository#findByEnabledTrueOrderByStoreIdAscAdvertNumberAsc",
                    "open_slot_advert(store_id, advert_number)"),
            Map.entry("PickupPolicyExcludedDateRepository#findByPolicyIdOrderByExcludedDate",
                    "pickup_policy_excluded_date(policy_id, excluded_date)"),
            Map.entry("PollOptionRepository#findByPollIdOrderByOptionIndex", "poll_option(poll_id, option_index)"),
            Map.entry("PollOptionValueRepository#findByPollIdOrderByOptionIndexAscValueDesc",
                    "poll_option_value(poll_id, option_index, option_value)"),
            Map.entry("PrivateInfoTemplateRepository#findByGuideIdOrderByCategoryAsc", "private_info_template(guide_id, category)"),
            Map.entry("IntroPickupStoreAliasRepository#findByRegionIdOrderByAliasKeyAsc",
                    "intro_pickup_store_alias(region_id, alias_key)"),
            Map.entry("RegionRepository#findAncestors", "region_closure: one depth per ancestor of a region"),
            // these select only the order key: equal values are indistinguishable, so their order cannot matter
            Map.entry("MailOutboxRepository#slotsSince", "selects only the order key"),
            Map.entry("MailOutboxRepository#templateSlotsSince", "selects only the order key"));

    private static final Pattern DERIVED_ORDER = Pattern.compile("OrderBy([A-Z][A-Za-z0-9]*)$");
    private static final Pattern DIRECTION_SPLIT = Pattern.compile("(?<=Asc|Desc)(?=[A-Z])");
    private static final Pattern JPQL_ORDER = Pattern.compile("(?is)\\border\\s+by\\s+(.*?)(?:\\blimit\\b.*)?$");
    private static final Pattern ID_KEY = Pattern.compile("(?i)^(?:[a-z_][a-z0-9_]*\\.)?id(?:\\s+(?:asc|desc))?$");

    @Test
    void everyOrderedRepositoryQueryEndsWithAUniqueKey() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Class<?> repository : repositoryInterfaces()) {
            for (Method method : repository.getDeclaredMethods()) {
                String key = repository.getSimpleName() + "#" + method.getName();
                Query query = method.getAnnotation(Query.class);
                String problem;
                if (query != null) {
                    problem = checkQuery(query.value());
                } else {
                    problem = checkDerivedName(method.getName());
                }
                boolean paged = Arrays.stream(method.getParameterTypes()).anyMatch(Pageable.class::isAssignableFrom);
                boolean ordered = query != null ? JPQL_ORDER.matcher(query.value()).find()
                        : method.getName().contains("OrderBy");
                if (problem == null && paged && !ordered) {
                    problem = "takes a Pageable but declares no order (a caller's Sort cannot be checked here)";
                }
                if (problem != null || ordered) {
                    checked++;
                }
                if (problem != null && !UNIQUE_ORDER_KEYS.containsKey(key)) {
                    problems.add(key + ": " + problem);
                }
                if (problem == null && UNIQUE_ORDER_KEYS.containsKey(key)) {
                    problems.add(key + ": is stable now, remove it from UNIQUE_ORDER_KEYS");
                }
            }
        }
        assertThat(checked).as("ordered repository queries found").isGreaterThan(50);
        assertThat(problems).as("order by a non-unique key only; add the id as the last order key").isEmpty();
    }

    /** `Sort.by(...)` in production code (Pageable/Specification sorts) also ends with the id. */
    @Test
    void everySortEndsWithTheId() throws IOException {
        Pattern sortBy = Pattern.compile("Sort\\.by\\(((?:[^()]|\\([^()]*\\))*)\\)");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = sortBy.matcher(Files.readString(file));
                while (matcher.find()) {
                    String args = matcher.group(1).replaceAll("\\s+", "");
                    if (!args.matches(".*[\"(]id\"\\)*$")) {
                        problems.add(file.getFileName() + ": Sort.by(" + args + ")");
                    }
                }
            }
        }
        assertThat(problems).as("Sort.by(...) must end with the id").isEmpty();
    }

    @Test
    void theCheckRecognisesStableAndUnstableOrders() {
        // the examples are spelled in two parts, so a rename over the sources never rewrites them
        String unstable = "findByOwner" + "IdOrderByCreatedAt";
        assertThat(checkDerivedName(unstable + "Desc")).isNotNull();
        assertThat(checkDerivedName(unstable)).isNotNull();
        assertThat(checkDerivedName(unstable + "DescIdDesc")).isNull();
        assertThat(checkDerivedName(unstable + "AscIdAsc")).isNull();
        assertThat(checkDerivedName("findFirstByOwnerIdOrderByValidUntilDesc" + "CreatedAtDesc")).isNotNull();
        assertThat(checkDerivedName("findByStatusOrderByCreatedAtDesc" + "IdentityDesc")).isNotNull();
        assertThat(checkDerivedName("findAllByOrderById")).isNull();
        assertThat(checkDerivedName("findByUserId")).isNull();
        assertThat(checkQuery("select b from B b order by b.createdAt desc")).isNotNull();
        assertThat(checkQuery("select b from B b order by b.createdAt desc, b.id desc")).isNull();
        assertThat(checkQuery("select id from t order by name, id limit :limit")).isNull();
        assertThat(checkQuery("select b from B b where b.x = :x")).isNull();
        assertThat(checkQuery("select b from B b order by b.userId")).isNotNull();
    }

    static String checkDerivedName(String name) {
        Matcher matcher = DERIVED_ORDER.matcher(name);
        if (!matcher.find()) {
            return null;
        }
        String[] keys = DIRECTION_SPLIT.split(matcher.group(1));
        String last = keys[keys.length - 1].replaceAll("(Asc|Desc)$", "");
        return last.equals("Id") ? null : "derived query orders by " + matcher.group(1) + " without a final Id";
    }

    static String checkQuery(String query) {
        Matcher matcher = JPQL_ORDER.matcher(query.trim());
        if (!matcher.find()) {
            return null;
        }
        String clause = matcher.group(1).trim();
        String[] keys = clause.split(",");
        String last = keys[keys.length - 1].trim();
        return ID_KEY.matcher(last).matches() ? null
                : "@Query orders by '" + clause.toLowerCase(Locale.ROOT) + "' without a final id";
    }

    private static List<Class<?>> repositoryInterfaces() throws ClassNotFoundException {
        List<Class<?>> result = new ArrayList<>();
        for (JavaClass javaClass : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(ArchitectureRules.ROOT)) {
            if (javaClass.isInterface() && javaClass.isAssignableTo(Repository.class)) {
                result.add(Class.forName(javaClass.getName()));
            }
        }
        assertThat(result).as("repository interfaces").hasSizeGreaterThan(50);
        return result;
    }
}
