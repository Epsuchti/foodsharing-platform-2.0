package network.foodsharing.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

/** C5: index built on start, permission-filtered results, incremental refresh, quick-access index. */
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
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private List<String> ids(String body, String path) {
        return JsonPath.read(body, path + "[*].id");
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
