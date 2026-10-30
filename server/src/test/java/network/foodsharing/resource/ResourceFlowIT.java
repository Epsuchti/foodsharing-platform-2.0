package network.foodsharing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** C6: region list (privacy), limit, commons (function group + bell), favourites, edit/delete rights. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:resources-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false", "app.resources.max-per-user=3"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class ResourceFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String SCHWEIZ = "00000010-0001-4000-8000-000000000002";
    private static final String TOOLS = "00000056-0001-4000-8000-000000000001";
    private static final String DRILL = "00000056-0003-4000-8000-000000000003";

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
    void regionListHonoursPrivacyAndRegionType() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String list = mvc.perform(get("/api/regions/" + ZUERICH + "/resources").cookie(foodsaver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids).hasSize(9).doesNotContain(DRILL);
        mvc.perform(get("/api/regions/" + ZUERICH + "/resources").cookie(login(SeedUsers.JUMPER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + DRILL + "')].mayEdit").value(true));
        mvc.perform(get("/api/regions/" + SCHWEIZ + "/resources").cookie(foodsaver))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RESOURCES_NOT_AVAILABLE"));
        mvc.perform(get("/api/regions/" + ZUERICH + "/resources").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/resources/categories").param("lang", "fr").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Outils"));
        mvc.perform(get("/api/resources/favourites").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void createLimitEditDeleteAndFavourite() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        String body = "{\"name\":\"Leiter\",\"categoryIds\":[\"" + TOOLS + "\"],\"private\":false,"
                + "\"openness\":\"ASK_FIRST\",\"commons\":false}";
        String created = mvc.perform(post("/api/resources").with(csrf()).cookie(jumper)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mayEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/resources").with(csrf()).cookie(jumper).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/resources").with(csrf()).cookie(jumper).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_LIMIT_REACHED"));
        // the limit travels with the permissions (app.resources.max-per-user=3 here)
        mvc.perform(get("/api/regions/" + ZUERICH + "/resources/permissions").cookie(jumper))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mayAdd").value(false))
                .andExpect(jsonPath("$.maxPerUser").value(3));
        // ... and without a region for /resources/mine
        Integer jumperOwn = jdbc.queryForObject("select count(*) from resource where owner_id = ? "
                + "and deleted_at is null", Integer.class, SeedUsers.JUMPER.id());
        mvc.perform(get("/api/resources/mine/limits").cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPerUser").value(3)).andExpect(jsonPath("$.mayAdd").value(false))
                .andExpect(jsonPath("$.ownCount").value(jumperOwn));
        Integer biebOwn = jdbc.queryForObject("select count(*) from resource where owner_id = ? "
                + "and deleted_at is null", Integer.class, SeedUsers.BIEB.id());
        mvc.perform(get("/api/resources/mine/limits").cookie(login(SeedUsers.BIEB))).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownCount").value(biebOwn)).andExpect(jsonPath("$.mayAdd").value(biebOwn < 3));

        // images carry the original file name of their upload
        String upload = jdbc.queryForObject("select photo_upload_id from basket where id = ?", String.class,
                "00000048-0001-4000-8000-000000000001");
        String fileName = jdbc.queryForObject("select file_name from upload where id = ?", String.class,
                java.util.UUID.fromString(upload));
        jdbc.update("insert into resource_image (id, version, created_at, updated_at, resource_id, upload_id, "
                + "sort_order) values (random_uuid(), 0, now(), now(), ?, ?, 0)", java.util.UUID.fromString(id),
                java.util.UUID.fromString(upload));
        mvc.perform(get("/api/resources/mine").cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].images[0].fileName").value(fileName));
        mvc.perform(post("/api/resources").with(csrf()).cookie(login(SeedUsers.BIEB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"categoryIds\":[\"00000000-0000-4000-8000-000000000000\"],"
                        + "\"private\":false,\"openness\":\"ASK_FIRST\",\"commons\":false}"))
                .andExpect(status().isBadRequest());

        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(patch("/api/resources/" + id).with(csrf()).cookie(foodsaver)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Meine\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/resources/" + id).with(csrf()).cookie(jumper)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alu-Leiter\",\"openness\":\"GIVE_AWAY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alu-Leiter"))
                .andExpect(jsonPath("$.openness").value("GIVE_AWAY"));
        mvc.perform(put("/api/resources/" + id + "/favourite").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/resources/" + id + "/favourite").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from resource_favorite where resource_id = ?", Integer.class,
                java.util.UUID.fromString(id))).isEqualTo(1);
        mvc.perform(put("/api/resources/" + DRILL + "/favourite").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/resources/" + id).with(csrf()).cookie(foodsaver)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/resources/" + id).with(csrf()).cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/resources/mine").cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where subject_id = ?", Integer.class,
                java.util.UUID.fromString(id))).isGreaterThanOrEqualTo(4);
    }

    @Test
    void commonsNeedTheFunctionGroupAndBellTheRegion() throws Exception {
        String body = "{\"name\":\"Pavillon\",\"categoryIds\":[\"" + TOOLS + "\"],\"private\":false,"
                + "\"openness\":\"ASK_FIRST\",\"commons\":true,\"regionId\":\"" + ZUERICH + "\"}";
        mvc.perform(post("/api/resources").with(csrf()).cookie(login(SeedUsers.FOODSAVER))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/resources").with(csrf()).cookie(login(SeedUsers.ORGA))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.owner").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from bell where identifier = ?", Integer.class,
                "NEW_RESOURCE-" + ZUERICH)).isPositive();
    }
}
