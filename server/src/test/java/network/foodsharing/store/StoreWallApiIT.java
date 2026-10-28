package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.testsupport.SeedUsers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Wall, store log, statistics and store delete on the seed (B6, e2e profile with the fixed clock). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-wall-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StoreWallApiIT {

    static final String S01 = "00000020-0001-4000-8000-000000000001";
    static final String S17 = "00000020-0001-4000-8000-000000000017";
    static final String S18 = "00000020-0001-4000-8000-000000000018";
    static final String OLD_FOODSAVER_POST = "00000020-0008-4000-8000-000000000083";
    static final String FRESH_BIEB_POST = "00000020-0008-4000-8000-000000000084";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void seedHasWallPostsWithMilestones() {
        assertThat(jdbc.queryForObject("select count(distinct store_id) from store_wall_post", Integer.class))
                .isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(*) from store_wall_post where milestone = 'COOPERATION_STARTED'",
                Integer.class)).isEqualTo(18);
    }

    @Test
    void wallVisibilityPostingAndDeleteRules() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String wall = mvc.perform(get("/api/stores/" + S01 + "/wall").param("size", "50").cookie(foodsaver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Boolean> own = JsonPath.read(wall, "$.items[?(@.id=='" + OLD_FOODSAVER_POST + "')].mayDelete");
        List<Boolean> other = JsonPath.read(wall, "$.items[?(@.id=='" + FRESH_BIEB_POST + "')].mayDelete");
        assertThat(own).containsExactly(true);
        assertThat(other).containsExactly(false);

        mvc.perform(get("/api/stores/" + S01 + "/wall").cookie(login(SeedUsers.JUMPER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/stores/" + S01 + "/wall/" + FRESH_BIEB_POST).with(csrf()).cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/stores/" + S01 + "/wall").cookie(foodsaver), "{\"text\":\"Hallo Team\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Hallo Team"))
                .andExpect(jsonPath("$.mayDelete").value(true)).andExpect(jsonPath("$.milestone").value("NONE"));

        Cookie bieb = login(SeedUsers.BIEB);
        // manager: posts older than 30 days of others may be deleted
        mvc.perform(delete("/api/stores/" + S01 + "/wall/" + OLD_FOODSAVER_POST).with(csrf()).cookie(bieb))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/stores/" + S01 + "/wall/" + OLD_FOODSAVER_POST).with(csrf()).cookie(bieb))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WALL_POST_NOT_FOUND"));

        String log = mvc.perform(get("/api/stores/" + S01 + "/log").param("actions", "DELETED_FROM_WALL",
                        "WALL_POST_ADDED").cookie(bieb))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(log, "$.items[*].action"))
                .containsExactlyInAnyOrder("DELETED_FROM_WALL", "WALL_POST_ADDED");
        mvc.perform(get("/api/stores/" + S01 + "/log").cookie(foodsaver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/stores/" + S01 + "/log").param("actions", "NOPE").cookie(bieb))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teamStatusChangeWritesAMilestone() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(patch("/api/stores/" + S01).cookie(bieb), "{\"teamStatus\":\"CLOSED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/stores/" + S01 + "/wall").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.milestone=='TEAM_STATUS_CHANGED')].text")
                        .value(Matchers.contains("CLOSED")));
    }

    @Test
    void statisticsShowPerMemberOnlyToManagers() throws Exception {
        mvc.perform(get("/api/stores/" + S01 + "/statistics").cookie(login(SeedUsers.BIEB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPickups").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.weightCategory.code").value("KG_10_20"))
                .andExpect(jsonPath("$.perMember").isNotEmpty());
        mvc.perform(get("/api/stores/" + S01 + "/statistics").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perMember").doesNotExist());
    }

    @Test
    void deleteOnlyWithoutSignupsAndByCoordination() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(json(delete("/api/stores/" + S01).cookie(orga), "{\"reason\":\"test\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_HAS_PICKUPS"));
        mvc.perform(json(delete("/api/stores/" + S18).cookie(login(SeedUsers.FOODSAVER)), "{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(delete("/api/stores/" + S17).cookie(orga), "{\"reason\":\"Geschlossen\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/stores/" + S17).cookie(orga))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_DELETED"));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'DELETE_STORE' "
                + "and cast(subject_id as varchar(36)) = ?", Integer.class, S17)).isEqualTo(1);
    }
}
