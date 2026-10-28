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
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Chains, KAMs and category / grocery admin on the seed (B7). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-chain-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StoreChainApiIT {

    static final String COOP = "00000020-0003-4000-8000-000000000001";
    static final String MIGROS = "00000020-0003-4000-8000-000000000002";
    static final String SUPERMARKT = "00000020-0002-4000-8000-000000000001";

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
    void chainRightsForAdminKamAndFoodsaver() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/store-chains").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6)).andExpect(jsonPath("$.items[0].notes").doesNotExist());
        mvc.perform(get("/api/store-chains/" + COOP).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.kams[0].id").value(SeedUsers.BIEB.id().toString()));
        mvc.perform(json(patch("/api/store-chains/" + COOP).cookie(foodsaver), "{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/store-chains/" + COOP + "/stores").cookie(foodsaver)).andExpect(status().isForbidden());

        Cookie bot = login(SeedUsers.BOT); // KAM of Migros
        mvc.perform(json(patch("/api/store-chains/" + MIGROS).cookie(bot), "{\"estimatedStoreCount\":700}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estimatedStoreCount").value(700));
        mvc.perform(json(patch("/api/store-chains/" + MIGROS).cookie(bot),
                        "{\"kamIds\":[\"" + SeedUsers.BIEB.id() + "\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("KAM_CANNOT_EDIT_KAMS"));

        Cookie reports = login(SeedUsers.REPORTS); // admin of the STORE_CHAINS group
        String created = mvc.perform(json(post("/api/store-chains").cookie(reports),
                        "{\"name\":\"Spar\",\"status\":\"WAITING\",\"kamIds\":[\"" + SeedUsers.BIEB.id() + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kams[0].id").value(SeedUsers.BIEB.id().toString()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(json(post("/api/store-chains").cookie(reports),
                        "{\"name\":\"Volg 2\",\"kamIds\":[\"" + SeedUsers.FOODSAVER.id() + "\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_KAM"));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'KAM_ADDED' "
                + "and cast(subject_id as varchar(36)) = ?", Integer.class, id)).isEqualTo(1);
        mvc.perform(get("/api/store-chains/" + COOP + "/stores").cookie(login(SeedUsers.BIEB)))
                .andExpect(status().isOk());
    }

    /** PATCH lists: absent = unchanged, [] = clear (containerDefaultToNull). */
    @Test
    void patchListsAbsentKeepsEmptyClears() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        String created = mvc.perform(json(post("/api/store-chains").cookie(orga),
                        "{\"name\":\"Manor\",\"kamIds\":[\"" + SeedUsers.BIEB.id() + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String chain = "/api/store-chains/" + JsonPath.read(created, "$.id");
        mvc.perform(json(patch(chain).cookie(orga), "{\"pressAllowed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kams.length()").value(1));
        mvc.perform(json(patch(chain).cookie(orga), "{\"kamIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kams.length()").value(0));

        String store = "/api/stores/00000020-0001-4000-8000-000000000001";
        mvc.perform(json(patch(store).cookie(orga), "{\"publicInfo\":\"Hintereingang\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groceries").isNotEmpty());
        mvc.perform(json(patch(store).cookie(orga), "{\"groceryIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groceries").isEmpty());
    }

    @Test
    void categoryAdminWithMerge() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/store-categories").cookie(login(SeedUsers.BIEB))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/store-categories").cookie(orga), "{\"name\":\"supermarkt\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        String created = mvc.perform(json(post("/api/store-categories").cookie(orga), "{\"name\":\"Kiosk\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String kiosk = JsonPath.read(created, "$.id");
        mvc.perform(delete("/api/store-categories/" + SUPERMARKT).with(csrf()).cookie(orga))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        int before = jdbc.queryForObject("select count(*) from store where category_id = ?", Integer.class,
                java.util.UUID.fromString(SUPERMARKT));
        mvc.perform(json(post("/api/store-categories/" + SUPERMARKT + "/merge").cookie(orga),
                        "{\"targetId\":\"" + kiosk + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.storeCount").value(before));
        mvc.perform(get("/api/store-categories").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + SUPERMARKT + "')]").isEmpty());
        mvc.perform(get("/api/groceries").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }
}
