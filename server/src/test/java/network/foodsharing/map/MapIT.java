package network.foodsharing.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** M1: seed counts per layer, rounding, user layer only for region administrators, public bubbles. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:map-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class MapIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";

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

    @Test
    void publicLayers() throws Exception {
        mvc.perform(get("/api/public/map/markers/food-share-points")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        String baskets = mvc.perform(get("/api/public/map/markers/baskets")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Double> lats = JsonPath.read(baskets, "$[*].lat");
        // 10 open baskets; HOME baskets are rounded (CUSTOM pick-up points are public addresses and stay exact).
        assertThat(lats).hasSize(10).contains(47.389, 47.3769);
        mvc.perform(get("/api/public/map/markers/regions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'REGION_PIN')]").isNotEmpty());
        mvc.perform(get("/api/public/map/bubbles/fsp/00000049-0001-4000-8000-000000000001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Fairteiler Wiedikon"))
                .andExpect(jsonPath("$.link").value("/food-share-points/00000049-0001-4000-8000-000000000001"));
        mvc.perform(get("/api/public/map/bubbles/store/00000020-0001-4000-8000-000000000001"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/map/bubbles/fsp/00000049-0001-4000-8000-000000000005"))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInLayers() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        int live = jdbc.queryForObject("select count(*) from store where deleted_at is null and latitude is not null "
                + "and cooperation_status <> 'PERMANENTLY_CLOSED'", Integer.class);
        mvc.perform(get("/api/map/markers/stores").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(live));
        String cooperating = mvc.perform(get("/api/map/markers/stores").param("status", "COOPERATING")
                .cookie(foodsaver)).andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(cooperating, "$[*].cooperationStatus");
        assertThat(statuses).isNotEmpty().allMatch("COOPERATION_ESTABLISHED"::equals);
        mvc.perform(get("/api/map/markers/stores").param("scope", "MEMBER").cookie(foodsaver))
                .andExpect(status().isOk());
        mvc.perform(get("/api/map/markers/events").cookie(foodsaver)).andExpect(status().isOk());
        mvc.perform(get("/api/map/bubbles/store/00000020-0001-4000-8000-000000000001").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.link")
                        .value("/stores/00000020-0001-4000-8000-000000000001"));
        mvc.perform(get("/api/map/markers/users").param("regionId", ZUERICH).cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/map/bubbles/user/" + SeedUsers.JUMPER.id()).cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/map/markers/users").param("regionId", ZUERICH).cookie(login(SeedUsers.BOT)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/map/search").param("q", "Zürich").cookie(foodsaver)).andExpect(status().isOk());
    }

    @Test
    void activityFeedFollowsFiltersA19() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String feed = mvc.perform(get("/api/activity").param("size", "50").cookie(foodsaver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(feed, "$.items[*].type");
        List<String> times = JsonPath.read(feed, "$.items[*].at");
        assertThat(types).contains("FSP_WALL");
        assertThat(times).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/me/activity-filters")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .csrf())
                        .cookie(foodsaver).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excluded\":[\"FSP_WALL\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.excluded[0]").value("FSP_WALL"));
        mvc.perform(get("/api/users/me/activity-filters").cookie(foodsaver))
                .andExpect(jsonPath("$.excluded.length()").value(1));
        String filtered = mvc.perform(get("/api/activity").param("size", "50").cookie(foodsaver))
                .andReturn().getResponse().getContentAsString();
        List<String> remaining = JsonPath.read(filtered, "$.items[*].type");
        assertThat(remaining).doesNotContain("FSP_WALL");
    }
}
