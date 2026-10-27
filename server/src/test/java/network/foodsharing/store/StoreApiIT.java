package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
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

/** Store core on the seed (e2e profile): permissions per team status, lists, detail gating, create, update, move. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StoreApiIT {

    static final String ZURICH = "00000010-0001-4000-8000-000000000040";
    static final String WINTERTHUR = "00000010-0001-4000-8000-000000000041";
    static final String S01 = "00000020-0001-4000-8000-000000000001";
    static final String S04 = "00000020-0001-4000-8000-000000000004";

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
    void seedHas25StoresWithEveryCooperationStatus() {
        assertThat(jdbc.queryForObject("select count(*) from store where cast(id as varchar(36)) like '00000020-%'",
                Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select count(distinct cooperation_status) from store "
                + "where cast(id as varchar(36)) like '00000020-%'", Integer.class))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from store where latitude < 47.3 or latitude > 47.6 "
                + "or longitude < 8.4 or longitude > 8.8", Integer.class)).isZero();
    }

    @Test
    void permissionsFollowTheTeamStatus() throws Exception {
        String url = "/api/stores/" + S01 + "/permissions";
        mvc.perform(get(url).cookie(login(SeedUsers.BIEB)))
                .andExpect(jsonPath("$.teamStatus").value("COORDINATOR"))
                .andExpect(jsonPath("$.mayEditStore").value(true)).andExpect(jsonPath("$.maySeeStoreLog").value(true));
        mvc.perform(get(url).cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(jsonPath("$.teamStatus").value("MEMBER"))
                .andExpect(jsonPath("$.mayReadWall").value(true)).andExpect(jsonPath("$.maySeePickups").value(true))
                .andExpect(jsonPath("$.mayEditStore").value(false))
                .andExpect(jsonPath("$.maySeePhoneNumbers").value(true));
        mvc.perform(get(url).cookie(login(SeedUsers.JUMPER)))
                .andExpect(jsonPath("$.teamStatus").value("WAITING_LIST"))
                .andExpect(jsonPath("$.mayAccess").value(true)).andExpect(jsonPath("$.mayReadWall").value(false))
                .andExpect(jsonPath("$.maySeePickups").value(false));
        mvc.perform(get(url).cookie(login(SeedUsers.NEWBIE)))
                .andExpect(jsonPath("$.mayAccess").value(false)).andExpect(jsonPath("$.mayApply").value(true));
        // Zürich has a STORES_COORDINATION group: the ambassador bot does not coordinate stores he is not in
        mvc.perform(get("/api/stores/" + S01 + "/permissions").cookie(login(SeedUsers.BOT)))
                .andExpect(jsonPath("$.mayEditStore").value(false));
        mvc.perform(get(url).cookie(login(SeedUsers.ORGA)))
                .andExpect(jsonPath("$.mayEditStore").value(true)).andExpect(jsonPath("$.mayDelete").value(true));
    }

    @Test
    void detailHidesSensitiveFieldsWithoutWallAccess() throws Exception {
        mvc.perform(get("/api/stores/" + S01).cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Coop Bahnhofbrücke"))
                .andExpect(jsonPath("$.contact.phone").isNotEmpty())
                .andExpect(jsonPath("$.requiresVerified").value(true))
                .andExpect(jsonPath("$.managers[0].id").value(SeedUsers.BIEB.id().toString()))
                .andExpect(jsonPath("$.teamCounts.jumpers").value(1))
                .andExpect(jsonPath("$.timeZone").value("Europe/Zurich"));
        mvc.perform(get("/api/stores/" + S01).cookie(login(SeedUsers.JUMPER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.specialNotes").doesNotExist());
        mvc.perform(get("/api/stores/" + S01).cookie(login(SeedUsers.NEWBIE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/stores/00000020-0001-4000-8000-000000000999").cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void regionListFiltersAndShowsMembership() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String list = mvc.perform(get("/api/regions/" + ZURICH + "/stores").param("size", "100").cookie(foodsaver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // default filter: UNCLEAR, NO_CONTACT, IN_NEGOTIATION, COOPERATION_ESTABLISHED (16 + 2 + 1 + 1)
        assertThat(JsonPath.<Integer>read(list, "$.page.totalItems")).isEqualTo(20);
        assertThat(JsonPath.<java.util.List<String>>read(list, "$.items[?(@.id == '" + S01 + "')].membership"))
                .containsExactly("MEMBER");
        mvc.perform(get("/api/regions/" + ZURICH + "/stores").param("cooperationStatus", "PERMANENTLY_CLOSED")
                        .param("includeSubregions", "true").cookie(foodsaver))
                .andExpect(jsonPath("$.page.totalItems").value(0));
        mvc.perform(get("/api/regions/" + ZURICH + "/stores").param("search", "vohdin").cookie(foodsaver))
                .andExpect(jsonPath("$.items[0].name").value("Bäckerei Vohdin"));
        mvc.perform(get("/api/regions/" + ZURICH + "/stores").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/stores").cookie(login(SeedUsers.JUMPER)))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/users/me/stores").param("excludeInactive", "true").cookie(login(SeedUsers.JUMPER)))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/store-metadata").cookie(foodsaver))
                .andExpect(jsonPath("$.categories.length()").value(8))
                .andExpect(jsonPath("$.weightCategories[4].averageKg").value(15.0))
                .andExpect(jsonPath("$.chains.length()").value(6));
        mvc.perform(get("/api/store-metadata").cookie(login(SeedUsers.NEWBIE))).andExpect(status().isForbidden());
    }

    @Test
    void createUpdateAndMove() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(post("/api/regions/" + ZURICH + "/stores").cookie(login(SeedUsers.FOODSAVER)),
                        "{\"name\":\"X\",\"address\":{},\"cooperationStatus\":\"UNCLEAR\",\"teamStatus\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/regions/" + WINTERTHUR + "/stores").cookie(bieb),
                        "{\"name\":\"X\",\"address\":{},\"cooperationStatus\":\"UNCLEAR\",\"teamStatus\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        Cookie orga = login(SeedUsers.ORGA);
        String created = mvc.perform(json(post("/api/regions/" + WINTERTHUR + "/stores").cookie(orga),
                        "{\"name\":\"Neuer Laden\",\"address\":{\"street\":\"Gasse 1\",\"postcode\":\"8001\","
                                + "\"city\":\"Zürich\",\"country\":\"CH\"},\"latitude\":47.37,\"longitude\":8.54,"
                                + "\"cooperationStatus\":\"IN_NEGOTIATION\",\"teamStatus\":\"OPEN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.membership").value("COORDINATOR"))
                .andExpect(jsonPath("$.prefetchDays").value(14))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(json(patch("/api/stores/" + id).cookie(orga),
                        "{\"name\":\"Neuer Laden Winterthur\",\"cooperationStatus\":\"COOPERATION_ESTABLISHED\","
                                + "\"prefetchDays\":21}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prefetchDays").value(21));
        assertThat(jdbc.queryForList("select action from history_entry where subject_id = ?", String.class,
                java.util.UUID.fromString(id))).contains("STORE_CREATED", "STORE_UPDATED",
                "COOPERATION_STATUS_CHANGED", "UPDATE_REGULAR_PICKUP_INTERVAL", "FIELD_CHANGED");
        mvc.perform(json(patch("/api/stores/" + S04).cookie(login(SeedUsers.ORGA)),
                        "{\"regionId\":\"" + WINTERTHUR + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_MEMBERS_NOT_IN_TARGET_REGION"))
                .andExpect(jsonPath("$.args.names").value(Matchers.containsString("Sarah Müller")));
        mvc.perform(json(patch("/api/stores/" + S01).cookie(login(SeedUsers.FOODSAVER)), "{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }
}
