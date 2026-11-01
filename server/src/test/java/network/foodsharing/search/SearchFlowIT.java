package network.foodsharing.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.search.service.SearchIndexer;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C5: index built on start, permission-filtered results, incremental refresh, quick-access index; decision 147 on the
 * H2 (LIKE) path: own units first, store address with two words, contacts-only people search, exact scoped email
 * search, forum post contents behind SEARCH_FORUM_POSTS.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:search-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class SearchFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SearchIndexer indexer;

    @Autowired
    private java.time.Clock clock;

    private Cookie login(SeedUsers user) throws Exception {
        return login(user.id());
    }

    private Cookie login(UUID userId) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private List<String> ids(String body, String path) {
        return JsonPath.read(body, path + "[*].id");
    }

    private String search(Cookie who, String q, String... kinds) throws Exception {
        var request = get("/api/search").param("q", q).cookie(who);
        if (kinds.length > 0) {
            request.param("kinds", kinds);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> pick(Cookie who, String q) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/users/search").param("q", q).cookie(who)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[*].id");
    }

    private void forumPostsToggle(Cookie orga, boolean enabled) throws Exception {
        mvc.perform(patch("/api/admin/feature-toggles/SEARCH_FORUM_POSTS").with(csrf()).cookie(orga)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void ownRegionsAndGroupsComeFirst() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        java.util.Set<String> admin = new java.util.HashSet<>(jdbc.queryForList(
                "select cast(region_id as varchar) from region_admin where user_id = ?", String.class,
                SeedUsers.BOT.id()));
        String body = search(bot, "zurich", "REGION", "GROUP");
        for (String path : List.of("$.regions", "$.groups")) {
            List<String> found = ids(body, path);
            assertThat(found).as(path).isNotEmpty();
            int lastAdmin = -1;
            int firstOther = found.size();
            for (int i = 0; i < found.size(); i++) {
                if (admin.contains(found.get(i))) {
                    lastAdmin = i;
                } else {
                    firstOther = Math.min(firstOther, i);
                }
            }
            assertThat(lastAdmin).as(path + " admin units first").isGreaterThanOrEqualTo(0).isLessThan(firstOther);
        }
    }

    @Test
    void storeAddressMatchesOnlyWithTwoOrMoreWords() throws Exception {
        Map<String, Object> store = jdbc.queryForMap("select id, name, street from store where deleted_at is null"
                + " and street is not null order by name limit 1");
        String street = ((String) store.get("street")).split(" ")[0];
        String name = ((String) store.get("name")).split(" ")[0];
        Cookie orga = login(SeedUsers.ORGA);

        assertThat(ids(search(orga, street, "STORE"), "$.stores")).doesNotContain(store.get("id").toString());
        assertThat(ids(search(orga, name + " " + street, "STORE"), "$.stores")).contains(store.get("id").toString());
    }

    @Test
    void peopleSearchIsLimitedToContacts() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String newbie = SeedUsers.NEWBIE.id().toString();
        assertThat(ids(search(foodsaver, "Lea Brunner", "USER"), "$.users")).doesNotContain(newbie);
        assertThat(pick(foodsaver, "Lea")).doesNotContain(newbie);
        Timestamp now = Timestamp.from(clock.instant());

        // a shared conversation makes a contact
        UUID conversation = jdbc.queryForObject("select conversation_id from conversation_member where user_id = ?"
                + " order by conversation_id limit 1", UUID.class, SeedUsers.FOODSAVER.id());
        UUID member = UUID.randomUUID();
        jdbc.update("insert into conversation_member (id, version, created_at, updated_at, conversation_id, user_id,"
                + " joined_at) values (?, 0, ?, ?, ?, ?, ?)", member, now, now, conversation, SeedUsers.NEWBIE.id(), now);
        assertThat(ids(search(foodsaver, "Lea Brunner", "USER"), "$.users")).contains(newbie);
        assertThat(pick(foodsaver, "Lea")).contains(newbie);
        jdbc.update("delete from conversation_member where id = ?", member);

        // so does a store team (member or jumper)
        UUID store = jdbc.queryForObject("select store_id from store_membership where user_id = ? and status = 'MEMBER'"
                + " order by store_id limit 1", UUID.class, SeedUsers.FOODSAVER.id());
        UUID team = UUID.randomUUID();
        jdbc.update("insert into store_membership (id, version, created_at, updated_at, store_id, user_id, status,"
                + " manager) values (?, 0, ?, ?, ?, ?, 'JUMPER', false)", team, now, now, store, SeedUsers.NEWBIE.id());
        assertThat(pick(foodsaver, "Lea")).contains(newbie);
        jdbc.update("delete from store_membership where id = ?", team);

        // and a confirmed buddy, not a requested one
        UUID buddy = UUID.randomUUID();
        jdbc.update("insert into buddy (id, version, created_at, updated_at, user_id, buddy_id, status)"
                + " values (?, 0, ?, ?, ?, ?, 'REQUESTED')", buddy, now, now, SeedUsers.NEWBIE.id(),
                SeedUsers.FOODSAVER.id());
        assertThat(pick(foodsaver, "Lea")).doesNotContain(newbie);
        jdbc.update("update buddy set status = 'CONFIRMED' where id = ?", buddy);
        assertThat(pick(foodsaver, "Lea")).contains(newbie);
        assertThat(ids(search(foodsaver, "Lea Brunner", "USER"), "$.users")).contains(newbie);
        jdbc.update("delete from buddy where id = ?", buddy);

        // ORGA finds everyone; the full name is shown
        String orga = search(login(SeedUsers.ORGA), "Lea Brunner", "USER");
        assertThat(ids(orga, "$.users")).contains(newbie);
        assertThat(JsonPath.<List<String>>read(orga, "$.users[*].title")).contains("Lea Brunner");
    }

    @Test
    void emailSearchIsExactAndScoped() throws Exception {
        String email = jdbc.queryForObject("select email from user_account where id = ?", String.class,
                SeedUsers.FOODSAVER.id());
        String newbieEmail = jdbc.queryForObject("select email from user_account where id = ?", String.class,
                SeedUsers.NEWBIE.id());
        String foodsaver = SeedUsers.FOODSAVER.id().toString();
        String newbie = SeedUsers.NEWBIE.id().toString();

        Cookie orga = login(SeedUsers.ORGA);
        assertThat(ids(search(orga, email.toUpperCase(java.util.Locale.ROOT), "USER"), "$.users"))
                .containsExactly(foodsaver);
        assertThat(ids(search(orga, email.substring(0, email.length() - 2), "USER"), "$.users")).isEmpty();
        assertThat(ids(search(orga, newbieEmail, "USER"), "$.users")).containsExactly(newbie);

        // CAN_MANAGE_USERS (FSMANAGEMENT group of zuerich-stadt): only members of that region
        Cookie manager = login(SeedUsers.WELCOME);
        assertThat(ids(search(manager, email, "USER"), "$.users")).containsExactly(foodsaver);
        assertThat(pick(manager, email)).containsExactly(foodsaver);
        assertThat(ids(search(manager, newbieEmail, "USER"), "$.users")).isEmpty();
        assertThat(pick(manager, newbieEmail)).isEmpty();

        // everyone else: nothing by email, even for a contact
        Cookie jumper = login(SeedUsers.JUMPER);
        assertThat(ids(search(jumper, email, "USER"), "$.users")).isEmpty();
        assertThat(pick(jumper, email)).isEmpty();
    }

    @Test
    void forumPostContentsOnlyWithTheToggle() throws Exception {
        Map<String, Object> post = jdbc.queryForMap("select p.id, p.thread_id from forum_post p join forum_thread t"
                + " on t.id = p.thread_id where t.region_id = ? and t.ambassador_forum = false and t.active = true"
                + " and t.deleted_at is null and p.hidden_at is null and p.deleted_at is null order by p.id limit 1",
                UUID.fromString(ZUERICH));
        jdbc.update("update forum_post set body = 'Wer hat den Quokkaschluessel vom Lager gesehen?' where id = ?",
                post.get("id"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        Cookie orga = login(SeedUsers.ORGA);
        String thread = post.get("thread_id").toString();

        assertThat(ids(search(foodsaver, "quokkaschluessel", "THREAD"), "$.threads")).doesNotContain(thread);
        forumPostsToggle(orga, true);
        try {
            String body = search(foodsaver, "quokkaschluessel", "THREAD");
            assertThat(ids(body, "$.threads")).contains(thread);
            assertThat(JsonPath.<List<String>>read(body, "$.threads[*].subtitle"))
                    .anySatisfy(subtitle -> assertThat(subtitle).contains("Quokkaschluessel vom Lager"));
            assertThat(ids(search(login(SeedUsers.NEWBIE), "quokkaschluessel", "THREAD"), "$.threads")).isEmpty();
            jdbc.update("update forum_post set hidden_at = ? where id = ?", Timestamp.from(clock.instant()),
                    post.get("id"));
            assertThat(ids(search(foodsaver, "quokkaschluessel", "THREAD"), "$.threads")).doesNotContain(thread);
        } finally {
            jdbc.update("update forum_post set hidden_at = null where id = ?", post.get("id"));
            forumPostsToggle(orga, false);
        }
    }

    @Test
    void storesAndUsersAreVisibleToRegionMembersOnly() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from search_index", Integer.class)).isPositive();
        Map<String, Object> store = jdbc.queryForMap("select id, name from store where region_id = ? and deleted_at is null"
                + " order by name limit 1", java.util.UUID.fromString(ZUERICH));
        String word = ((String) store.get("name")).split(" ")[0];

        String asMember = mvc.perform(get("/api/search").param("q", word).cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(asMember, "$.stores")).contains(store.get("id").toString());

        String asNewbie = mvc.perform(get("/api/search").param("q", word).cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(asNewbie, "$.stores")).doesNotContain(store.get("id").toString());

        mvc.perform(get("/api/search").param("q", "zurich").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.regions[?(@.id == '" + ZUERICH + "')]").exists());

        String users = mvc.perform(get("/api/search").param("q", "muller sarah").param("kinds", "USER")
                .cookie(login(SeedUsers.JUMPER))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(ids(users, "$.users")).contains(SeedUsers.FOODSAVER.id().toString());
        assertThat(ids(users, "$.stores")).isEmpty();
        String newbieUsers = mvc.perform(get("/api/search").param("q", "muller sarah").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(newbieUsers, "$.users")).isEmpty();

        mvc.perform(get("/api/search").param("q", "a").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "zurich")).andExpect(status().isUnauthorized());
    }

    @Test
    void incrementalRefreshAndReindex() throws Exception {
        indexer.refresh();
        Map<String, Object> store = jdbc.queryForMap("select id from store where region_id = ? and deleted_at is null"
                + " order by name limit 1", java.util.UUID.fromString(ZUERICH));
        jdbc.update("update store set name = 'Quokkabäckerei', updated_at = ? where id = ?",
                Timestamp.from(clock.instant().plusSeconds(1)), store.get("id"));
        indexer.refresh();
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/search").param("q", "quokkabackerei").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stores.length()").value(1));
        mvc.perform(post("/api/dev/search/reindex").with(csrf()).cookie(orga)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'REINDEXED'",
                Integer.class)).isPositive();
        mvc.perform(get("/api/search/index").cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.regions[?(@.id == '" + ZUERICH + "')]").exists())
                .andExpect(jsonPath("$.stores").isArray());
    }
}
