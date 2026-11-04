package network.foodsharing.automation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * Decision 165 S4 (Q3): the link picker finds stores across regions, bounded by StoreVisibility; a store manager links
 * any of them one-way, a bidirectional rule needs mayEditStore of every linked store. Seed: bieb manages S-06 Babu's
 * Bakery in zuerich-stadt; Coop Winterthur Bahnhof (S-23) is in winterthur, a sibling region; Bäckerei Winterthur Markt
 * (S-24) is permanently closed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:link-candidates-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class LinkCandidateFlowIT {

    static final String S06 = "/api/stores/00000020-0001-4000-8000-000000000006";
    static final String S01_ID = "00000020-0001-4000-8000-000000000001";
    static final String S23_ID = "00000020-0001-4000-8000-000000000023";
    static final String S24_ID = "00000020-0001-4000-8000-000000000024";

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

    private static String policy(String storeId, boolean bidirectional) {
        return """
                {"enabled":true,"gapRuleEnabled":true,"minimumGapDays":2,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":true,"publicHolidayRuleEnabled":true,"excludedDates":[],
                 "linkedStores":[{"storeIds":["%s"],"bidirectional":%s,"windowMode":"SAME_DAY",
                 "windowDaysBefore":0,"windowDaysAfter":0,"graceMinutes":120}]}""".formatted(storeId, bidirectional);
    }

    @Test
    void searchFindsStoresOfOtherRegionsWithTheirRegionPath() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "winterthur").cookie(bieb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].store.id").value(Matchers.hasItem(S23_ID)))
                .andExpect(jsonPath("$.items[*].store.id").value(Matchers.not(Matchers.hasItem(S24_ID))))
                .andExpect(jsonPath("$.items[?(@.store.id == '" + S23_ID + "')].regionPath[-1]")
                        .value(Matchers.contains("Winterthur")))
                .andExpect(jsonPath("$.items[?(@.store.id == '" + S23_ID + "')].city")
                        .value(Matchers.contains("Winterthur")))
                .andExpect(jsonPath("$.items[?(@.store.id == '" + S23_ID + "')].mayEdit")
                        .value(Matchers.contains(false)))
                .andExpect(jsonPath("$.items[?(@.store.id == '" + S23_ID + "')].automationEnabled")
                        .value(Matchers.contains(false)));
        // this store is never offered; a store bieb manages is editable; wildcards are literal
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "Babu").cookie(bieb))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.totalItems").value(0));
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "Bahnhofbr").cookie(bieb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.store.id == '" + S01_ID + "')].mayEdit")
                        .value(Matchers.contains(true)));
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "%%").cookie(bieb))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        // paged
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "coop").param("size", "1").cookie(bieb))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page.totalItems").value(Matchers.greaterThan(1)));
    }

    @Test
    void searchNeedsMayEditStoreAndTwoCharacters() throws Exception {
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "c").cookie(login(SeedUsers.BIEB)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "coop").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        mvc.perform(get(S06 + "/automation/link-candidates").param("q", "coop").cookie(login(SeedUsers.SLEEPY)))
                .andExpect(status().isForbidden());
    }

    @Test
    void oneWayLinksAcrossRegionsBidirectionalNeedsTheOtherStore() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(put(S06 + "/automation/pickup-policy").cookie(bieb).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(policy(S23_ID, true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.args.storeId").value(S23_ID));
        mvc.perform(put(S06 + "/automation/pickup-policy").cookie(bieb).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(policy(S23_ID, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedStores[0].stores[0].name").value("Coop Winterthur Bahnhof"))
                .andExpect(jsonPath("$.linkedStores[0].editableStoreIds").isEmpty());
        // no mirror on the other store's side
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from pickup_policy "
                + "where store_id = ?", Integer.class, java.util.UUID.fromString(S23_ID))).isZero();
        // a store bieb manages may be bidirectional
        mvc.perform(put(S06 + "/automation/pickup-policy").cookie(bieb).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(policy(S01_ID, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedStores[0].editableStoreIds[0]").value(S01_ID));
        mvc.perform(get(S06 + "/automation").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramChatCount").value(0));
        mvc.perform(get("/api/stores/" + S01_ID + "/automation").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramChatCount").value(1));
    }
}
