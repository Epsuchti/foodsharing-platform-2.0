package network.foodsharing.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.statistics.service.PublicStatisticsService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** C3 public statistics, the aggregation job (idempotent per day) and the region statistics tab (R3/R13). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:statistics-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StatisticsFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublicStatisticsService publicStatistics;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void publicStatisticsAreAnonymousAndAggregationIsIdempotent() throws Exception {
        publicStatistics.evict();
        mvc.perform(get("/api/public/statistics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pickups").isNumber()).andExpect(jsonPath("$.asOf").isString());
        Integer seeded = jdbc.queryForObject("select count(*) from statistics_daily", Integer.class);
        assertThat(seeded).isEqualTo(400);

        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(post("/api/dev/statistics/aggregate").with(csrf()).cookie(orga)).andExpect(status().isNoContent());
        Integer afterFirst = jdbc.queryForObject("select count(*) from statistics_daily", Integer.class);
        mvc.perform(post("/api/dev/statistics/aggregate").with(csrf()).cookie(orga)).andExpect(status().isNoContent());
        assertThat(afterFirst).isBetween(400, 401);
        assertThat(jdbc.queryForObject("select count(*) from statistics_daily", Integer.class)).isEqualTo(afterFirst);
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'STATISTICS_AGGREGATED'",
                Integer.class)).isEqualTo(2);
        Long liveStores = jdbc.queryForObject("select count(*) from store where deleted_at is null", Long.class);
        mvc.perform(get("/api/public/statistics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stores").value(liveStores));
        Integer counted = jdbc.queryForObject("select stat_pickup_count from user_account where id = ?",
                Integer.class, SeedUsers.FOODSAVER.id());
        assertThat(counted).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void regionStatisticsForMembersOnly() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZUERICH + "/statistics/age-bands").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].label").value("18-25"));
        mvc.perform(get("/api/regions/" + ZUERICH + "/statistics/gender").param("onlyHomeRegion", "true")
                .cookie(foodsaver)).andExpect(status().isOk()).andExpect(jsonPath("$[0].gender").isString());
        mvc.perform(get("/api/regions/" + ZUERICH + "/statistics/pickups").param("months", "6").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/regions/" + ZUERICH + "/statistics/pickups").param("months", "40").cookie(foodsaver))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/regions/" + ZUERICH + "/statistics/gender").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isForbidden());
    }
}
