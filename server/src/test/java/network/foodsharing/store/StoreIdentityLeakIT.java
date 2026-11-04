package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import network.foodsharing.api.model.Role;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/**
 * Decision 156 (S17): walks every GET of the contract as each kind of person who may not know the stores and asserts
 * that no response holds a seeded store name, street or chain. Exceptions: flagged names on the partners list and the
 * store of one's own booking. Path ids and required query parameters come from a map: an operation that isn't mapped
 * fails, so a new endpoint has to opt in.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-leak-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StoreIdentityLeakIT {

    private static final Path PATHS = Path.of("src/main/resources/openapi/paths");
    private static final Pattern PATH_PARAM = Pattern.compile("\\{(\\w+)}");
    private static final String S_01 = "00000020-0001-4000-8000-000000000001";
    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";

    /** Path parameters that are literal values; the kind of the map bubbles is overridden in {@link #pathValue}. */
    private static final Map<String, String> LITERALS = Map.ofEntries(
            Map.entry("storeId", S_01),
            Map.entry("regionId", ZUERICH),
            Map.entry("groupId", "00000010-0001-4000-8000-000000000024"),
            Map.entry("userId", SeedUsers.BIEB.id().toString()),
            Map.entry("slug", "zuerich-stadt"),
            Map.entry("kind", "FOODSAVER"),
            Map.entry("key", "rules"),
            Map.entry("pickupAt", "2026-11-09T18:00:00Z"),
            Map.entry("day", "2026-11-09"),
            Map.entry("folder", "INBOX"),
            Map.entry("name", "mail-outbox"),
            Map.entry("token", "no-such-token"),
            Map.entry("advertNumber", "1"));

    /** Path parameters taken from the first seeded row of a table. */
    private static final Map<String, String> TABLES = Map.ofEntries(
            Map.entry("chainId", "store_chain"),
            Map.entry("introPickupId", "intro_pickup"),
            Map.entry("slotId", "intro_pickup_slot"),
            Map.entry("achievementId", "achievement"),
            Map.entry("attachmentId", "mail_attachment"),
            Map.entry("basketId", "basket"),
            Map.entry("blogPostId", "blog_post"),
            Map.entry("clientId", "oauth_client"),
            Map.entry("conversationId", "conversation"),
            Map.entry("eventId", "event"),
            Map.entry("fspId", "food_share_point"),
            Map.entry("id", "survey"),
            Map.entry("jobId", "job_run"),
            Map.entry("mailId", "mail_message"),
            Map.entry("mailboxId", "mailbox"),
            Map.entry("newsletterId", "newsletter"),
            Map.entry("pollId", "poll"),
            Map.entry("questionId", "quiz_question"),
            Map.entry("reportId", "report"),
            Map.entry("requestId", "oauth_authorization_request"),
            Map.entry("surveyId", "survey"),
            Map.entry("threadId", "forum_thread"),
            Map.entry("uploadId", "upload"));

    private static final Map<String, String> QUERY_PARAMS = Map.ofEntries(
            Map.entry("q", "Coop"),
            Map.entry("regionId", ZUERICH),
            Map.entry("role", "AMBASSADOR"),
            Map.entry("lat", "47.37"),
            Map.entry("lon", "8.54"));

    /** Exist only in the local and e2e profiles. */
    private static final String DEV = "/api/dev/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserService users;

    private Pattern identity;

    @BeforeEach
    void identities() {
        List<String> values = new ArrayList<>();
        values.addAll(jdbc.queryForList("select name from store where cast(id as varchar(36)) like '00000020-0001-%'",
                String.class));
        values.addAll(jdbc.queryForList("select street from store where street is not null "
                + "and cast(id as varchar(36)) like '00000020-0001-%'", String.class));
        values.addAll(jdbc.queryForList("select name from store_chain", String.class));
        identity = alternatives(values);
    }

    private static Pattern alternatives(List<String> values) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])(" + String.join("|", values.stream().distinct().map(Pattern::quote)
                .toList()) + ")(?![\\p{L}\\p{N}])");
    }

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Get> gets() throws IOException {
        List<Get> gets = new ArrayList<>();
        try (Stream<Path> files = Files.list(PATHS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yaml")).sorted().toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    Object loaded = new Yaml().load(in);
                    if (loaded instanceof Map<?, ?> paths) {
                        paths.forEach((path, item) -> {
                            Object get = ((Map<String, Object>) item).get("get");
                            if (get instanceof Map<?, ?> operation && !String.valueOf(path).startsWith(DEV)) {
                                gets.add(new Get(String.valueOf(path), (Map<String, Object>) operation));
                            }
                        });
                    }
                }
            }
        }
        return gets;
    }

    private record Get(String path, Map<String, Object> operation) {

        String operationId() {
            return String.valueOf(operation.get("operationId"));
        }

        @SuppressWarnings("unchecked")
        List<String> requiredQuery() {
            Object parameters = operation.get("parameters");
            if (!(parameters instanceof List<?> list)) {
                return List.of();
            }
            return list.stream().map(p -> (Map<String, Object>) p)
                    .filter(p -> "query".equals(p.get("in")) && Boolean.TRUE.equals(p.get("required")))
                    .map(p -> String.valueOf(p.get("name"))).toList();
        }

        /** An optional {@code storeId} is walked too: it asks for a store by id. */
        @SuppressWarnings("unchecked")
        boolean takesStoreId() {
            Object parameters = operation.get("parameters");
            return parameters instanceof List<?> list && list.stream().map(p -> (Map<String, Object>) p)
                    .anyMatch(p -> "query".equals(p.get("in")) && "storeId".equals(p.get("name")));
        }

        List<String> pathParams() {
            List<String> names = new ArrayList<>();
            Matcher matcher = PATH_PARAM.matcher(path);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        }
    }

    @Test
    void everyGetIsMapped() throws Exception {
        Set<String> unmapped = new java.util.TreeSet<>();
        for (Get get : gets()) {
            get.pathParams().stream().filter(p -> !LITERALS.containsKey(p) && !TABLES.containsKey(p))
                    .forEach(p -> unmapped.add(get.operationId() + " path " + p));
            get.requiredQuery().stream().filter(p -> !QUERY_PARAMS.containsKey(p))
                    .forEach(p -> unmapped.add(get.operationId() + " query " + p));
        }
        assertThat(unmapped).as("add these to LITERALS, TABLES or QUERY_PARAMS").isEmpty();
    }

    @Test
    void nothingNamesAStoreToAnybodyUnverified() throws Exception {
        users.changeRole(SeedUsers.PENDING.id(), Role.FOODSAVER);
        Map<String, List<String>> leaks = new TreeMap<>();
        walk("anonymous", null, SeedUsers.PENDING, leaks);
        walk("newbie", login(SeedUsers.NEWBIE), SeedUsers.NEWBIE, leaks);
        walk("einab1", login(SeedUsers.EINAB1), SeedUsers.EINAB1, leaks);
        walk("unverified foodsaver", login(SeedUsers.PENDING), SeedUsers.PENDING, leaks);
        // Rows from before S17 are not migrated away: an unverified team member still has them (S17 D7).
        jdbc.update("update user_account set verified = false where id = ?", SeedUsers.SLEEPY.id());
        walk("unverified with team rows", login(SeedUsers.SLEEPY), SeedUsers.SLEEPY, leaks);
        assertThat(leaks).isEmpty();
    }

    private void walk(String viewer, Cookie cookie, SeedUsers user, Map<String, List<String>> leaks) throws Exception {
        for (Get get : gets()) {
            String uri = get.path();
            for (String name : get.pathParams()) {
                uri = uri.replace("{" + name + "}", pathValue(get, name, cookie, user));
            }
            var request = get(uri);
            for (String name : get.requiredQuery()) {
                request.param(name, QUERY_PARAMS.get(name));
            }
            if (get.takesStoreId()) {
                request.param("storeId", S_01);
            }
            if (cookie != null) {
                request.cookie(cookie);
            }
            MvcResult result = mvc.perform(request).andReturn();
            int code = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(code).as("%s as %s", get.operationId(), viewer).isNotEqualTo(429);
            Set<String> found = new HashSet<>();
            Matcher matcher = identity.matcher(body);
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            Set<String> allowed = allowed(get, user);
            found.removeIf(value -> allowed.stream().anyMatch(text -> text.contains(value)));
            if (!found.isEmpty()) {
                leaks.computeIfAbsent(get.operationId() + " (" + code + ")", k -> new ArrayList<>())
                        .add(viewer + ": " + found);
            }
        }
    }

    /** The viewer's own feed token, so the walk reads a real feed and not a 404 (anonymous has none). */
    private String calendarToken(Cookie cookie) throws Exception {
        String body = mvc.perform(put("/api/calendar/token").cookie(cookie).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }

    private String pathValue(Get get, String name, Cookie cookie, SeedUsers user) throws Exception {
        if (name.equals("token") && get.operationId().equals("getCalendarFeed") && cookie != null) {
            return calendarToken(cookie);
        }
        if (name.equals("userId") && get.operationId().equals("getAgenda") && cookie != null) {
            return user.id().toString();
        }
        if (name.equals("day") && get.operationId().equals("getAgenda") && cookie != null) {
            // the day of the viewer's own sign-up, if any: an agenda of an empty day proves nothing
            List<String> days = jdbc.queryForList("select cast(cast(max(pickup_at) as date) as varchar(10)) "
                    + "from pickup_signup where user_id = ?", String.class, user.id());
            if (!days.isEmpty() && days.getFirst() != null) {
                return days.getFirst();
            }
        }
        if (get.operationId().endsWith("MapBubble")) {
            return name.equals("kind") ? "store" : S_01;
        }
        if (LITERALS.containsKey(name)) {
            return LITERALS.get(name);
        }
        List<String> ids = jdbc.queryForList("select cast(id as varchar(36)) from " + TABLES.get(name)
                + " order by 1 fetch first 1 rows only", String.class);
        return ids.isEmpty() ? "00000000-0000-4000-8000-000000000000" : ids.getFirst();
    }

    /** Texts a found value may be part of for this response. */
    private Set<String> allowed(Get get, SeedUsers user) {
        Set<String> allowed = new HashSet<>();
        // the seed reuses store streets for people and events: the same text is not a store's identity there
        allowed.addAll(jdbc.queryForList("select street from user_account where street is not null and id = ?",
                String.class, user.id()));
        if (get.operationId().matches("listRegionEvents|listMyEvents|getMyEventsIcs|getCalendarFeed")) {
            allowed.addAll(jdbc.queryForList("select street from event_location where street is not null",
                    String.class));
        }
        // Accepted in the plan's risks: rows from before S17 stay in their team chats until removed.
        if (user == SeedUsers.SLEEPY && get.operationId().equals("listConversations")) {
            allowed.addAll(jdbc.queryForList("select name from store", String.class));
        }
        if (get.operationId().equals("listPublicPartnerStores")) {
            allowed.addAll(jdbc.queryForList("select name from store where publicly_named = true", String.class));
        }
        if (get.operationId().matches("listMyBookings|getCalendarFeed")) {
            allowed.addAll(jdbc.queryForList("select concat_ws(' ', p.business_name, p.private_address, "
                    + "p.public_general_location, p.private_info, p.public_info) from intro_pickup p "
                    + "join intro_pickup_slot s on s.intro_pickup_id = p.id where s.booking_user_id = ?",
                    String.class, user.id()));
        }
        return allowed;
    }

    @Test
    void aSurveyWithAStoreQuestionCannotBeAnsweredForAStoreByTheUnverified() throws Exception {
        String survey = "00000046-0001-4000-8000-000000000001";
        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(get("/api/surveys/" + survey + "/form").param("storeId", S_01).cookie(newbie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.store").doesNotExist());
        mvc.perform(post("/api/surveys/" + survey + "/responses").cookie(newbie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":\"" + S_01 + "\",\"answers\":[]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void seedBubblesAreExactAndPositionedAtTheRegion() throws Exception {
        String body = mvc.perform(get("/api/public/map/store-areas")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(body, "$[*].name");
        List<Integer> counts = JsonPath.read(body, "$[*].count");
        assertThat(names).containsExactly("Winterthur", "Zürich Stadt");
        assertThat(counts).containsExactly(2, 16);
        assertThat(body).doesNotContain("Coop");
    }

    @Test
    void theExplainerSendsNoLongDurations() throws Exception {
        String body = mvc.perform(get("/api/public/onboarding/explainer")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("P21D").doesNotContain("P7D").contains("PT2M");
    }

    @Test
    void thePartnersListHoldsOnlyFlaggedStores() throws Exception {
        String body = mvc.perform(get("/api/public/partner-stores")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> names = JsonPath.read(body, "$[*].name");
        assertThat(names).containsExactly("Bäckerei Vohdin", "Coop Bahnhofbrücke");
    }
}
