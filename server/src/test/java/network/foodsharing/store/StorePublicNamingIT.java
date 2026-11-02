package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The {@code publicly_named} flag through the API (decision 153, S17 D6, D8). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-naming-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StorePublicNamingIT {

    /** S-01: bieb manages it; publicity ALLOWED, established. */
    private static final String S_01 = "00000020-0001-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie bieb;
    private Cookie orga;
    /** A Zürich store bieb coordinates (he administrates the STORES_COORDINATION group) but does not manage. */
    private String coordinated;

    @BeforeEach
    void setUp() throws Exception {
        bieb = login(SeedUsers.BIEB);
        orga = login(SeedUsers.ORGA);
        coordinated = jdbc.queryForObject("select cast(s.id as varchar(36)) from store s where s.deleted_at is null "
                + "and s.region_id = '00000010-0001-4000-8000-000000000040' and not exists (select 1 from "
                + "store_membership m where m.store_id = s.id and m.user_id = ?) order by s.id fetch first 1 rows only",
                String.class, SeedUsers.BIEB.id());
        jdbc.update("update store set publicly_named = false, publicity = 'ALLOWED' where id in (?, ?)",
                java.util.UUID.fromString(S_01), java.util.UUID.fromString(coordinated));
    }

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions update(Cookie cookie, String json) throws Exception {
        return update(S_01, cookie, json);
    }

    private org.springframework.test.web.servlet.ResultActions update(String store, Cookie cookie, String json)
            throws Exception {
        return mvc.perform(patch("/api/stores/" + store).with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int historyEntries(String store) {
        return jdbc.queryForObject("select count(*) from history_entry where action = 'STORE_PUBLIC_NAMING_CHANGED' "
                + "and subject_id = ?", Integer.class, java.util.UUID.fromString(store));
    }

    @Test
    void theMigrationDefaultsToFalse() {
        // The seed flags S-01 and S-02 only; setUp clears S-01.
        assertThat(jdbc.queryForList("select cast(id as varchar(36)) from store where publicly_named = true",
                String.class)).containsExactly("00000020-0001-4000-8000-000000000002");
    }

    @Test
    void aStoreManagerSwitchesItOnAndOffWithHistoryAndThePartnersListFollows() throws Exception {
        int before = historyEntries(S_01);
        update(bieb, "{\"publiclyNamed\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.publiclyNamed").value(true));
        mvc.perform(get("/api/public/partner-stores")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Coop Bahnhofbrücke')].city").value("Zürich"));
        update(bieb, "{\"publiclyNamed\":false}").andExpect(status().isOk());
        mvc.perform(get("/api/public/partner-stores"))
                .andExpect(jsonPath("$[?(@.name == 'Coop Bahnhofbrücke')]").isEmpty());
        assertThat(historyEntries(S_01)).isEqualTo(before + 2);
    }

    @Test
    void aRegionCoordinatorWhoDoesNotManageTheStoreMayNotButOrgaMay() throws Exception {
        mvc.perform(get("/api/stores/" + coordinated + "/permissions").cookie(bieb))
                .andExpect(jsonPath("$.mayEditStore").value(true))
                .andExpect(jsonPath("$.mayChangePublicNaming").value(false));
        update(coordinated, bieb, "{\"publiclyNamed\":true}").andExpect(status().isForbidden());
        update(coordinated, orga, "{\"publiclyNamed\":true}").andExpect(status().isOk());
        update(coordinated, bieb, "{\"publiclyNamed\":false}").andExpect(status().isForbidden());
    }

    @Test
    void goesOnOnlyWithPublicityAllowedAndLeavingAllowedClearsItForAnyEditor() throws Exception {
        update(bieb, "{\"publicity\":\"NOT_CHOSEN\",\"publiclyNamed\":true}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLICITY_NOT_ALLOWED"));
        update(coordinated, orga, "{\"publiclyNamed\":true}").andExpect(status().isOk());
        int before = historyEntries(coordinated);
        update(coordinated, bieb, "{\"publicity\":\"NOT_ALLOWED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.publiclyNamed").value(false));
        assertThat(historyEntries(coordinated)).isEqualTo(before + 1);
    }
}
