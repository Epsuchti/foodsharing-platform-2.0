package network.foodsharing.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M4 B5 end to end on the seed: dev clock to the trigger time → open-slot-adverts job → Telegram advert captured. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:advert-flow-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class AdvertFlowIT {

    static final String S06 = "00000020-0001-4000-8000-000000000006";

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

    private void clock(Instant at) throws Exception {
        mvc.perform(put("/api/dev/clock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fixedAt\":\"" + at + "\"}")).andExpect(status().isOk());
    }

    @Test
    void liveS06AdvertIsSentToTelegramAtTheTriggerTime() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String body = mvc.perform(get("/api/stores/" + S06 + "/pickups").cookie(bieb)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant pickupAt = null;
        for (JsonNode pickup : JsonMapper.builder().build().readTree(body)) {
            if (pickup.get("occupiedSlots").size() < pickup.get("slotCount").asInt()) {
                pickupAt = Instant.parse(pickup.get("pickupAt").asText());
                break;
            }
        }
        assertThat(pickupAt).as("a free S-06 pickup").isNotNull();
        String planned = mvc.perform(get("/api/stores/" + S06 + "/automation/adverts/planned?days=14").cookie(bieb))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(planned).contains("TELEGRAM");
        try {
            clock(pickupAt.minus(Duration.ofHours(23)));
            mvc.perform(post("/api/dev/jobs/open-slot-adverts/run").with(csrf())).andExpect(status().is2xxSuccessful());
            assertThat(jdbc.queryForObject("select count(*) from open_slot_advert_audit where store_id = ? "
                    + "and pickup_at = ? and status = 'SENT' and telegram_message_id is not null", Integer.class,
                    UUID.fromString(S06), java.sql.Timestamp.from(pickupAt))).isEqualTo(1);
            String captured = mvc.perform(get("/api/dev/notifications?channel=TELEGRAM"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(captured).contains("-1001").contains("Platz frei");
            mvc.perform(post("/api/dev/jobs/open-slot-adverts/run").with(csrf())).andExpect(status().is2xxSuccessful());
            assertThat(jdbc.queryForObject("select count(*) from open_slot_advert_audit where store_id = ? "
                    + "and pickup_at = ?", Integer.class, UUID.fromString(S06), java.sql.Timestamp.from(pickupAt)))
                    .isEqualTo(1);
        } finally {
            clock(Instant.parse("2026-11-02T09:00:00Z"));
        }
    }

    /** Coordinator bug report: advert #1 of S-06 with PUSH + TELEGRAM, "Run now" must still post to Telegram. */
    @Test
    void runNowWithPushAndTelegramSendsBoth() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        // independent of the other test of this class (same database): no earlier advert of S-06
        jdbc.update("delete from open_slot_advert_audit where store_id = ?", UUID.fromString(S06));
        mvc.perform(delete("/api/dev/notifications").with(csrf())).andExpect(status().is2xxSuccessful());
        String body = mvc.perform(get("/api/stores/" + S06 + "/pickups").cookie(bieb)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant pickupAt = null;
        for (JsonNode pickup : JsonMapper.builder().build().readTree(body)) {
            if (pickup.get("occupiedSlots").size() < pickup.get("slotCount").asInt()) {
                pickupAt = Instant.parse(pickup.get("pickupAt").asText());
                break;
            }
        }
        assertThat(pickupAt).as("a free S-06 pickup").isNotNull();
        var advert = (tools.jackson.databind.node.ObjectNode) JsonMapper.builder().build().readTree(mvc.perform(
                get("/api/stores/" + S06 + "/automation/adverts/1").cookie(bieb)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        advert.putArray("channels").add("PUSH").add("TELEGRAM");
        advert.putArray("pushMessages").add("{{storeName}} {{datetimeDe}}: Push, noch {{freeSlots}} Platz frei");
        mvc.perform(put("/api/stores/" + S06 + "/automation/adverts/1").cookie(bieb).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(advert.toString())).andExpect(status().isOk());
        try {
            clock(pickupAt.minus(Duration.ofHours(23)));
            String result = mvc.perform(post("/api/stores/" + S06 + "/automation/adverts/run").cookie(bieb)
                    .with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(result).contains("\"failed\":0").contains("\"confirmed\":1");
            String telegram = mvc.perform(get("/api/dev/notifications?channel=TELEGRAM"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(telegram).as("run result: %s", result).contains("-1001").contains("Platz frei");
            String again = mvc.perform(post("/api/stores/" + S06 + "/automation/adverts/run").cookie(bieb)
                    .with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(again).contains("\"confirmed\":0").contains("already sent for " + pickupAt);
        } finally {
            clock(Instant.parse("2026-11-02T09:00:00Z"));
        }
    }

    /** Coordinator request: mayUseAutomations, cleaningStore and mayAddExtraSlot are delivered. */
    @Test
    void automationFlagsAreDelivered() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get("/api/stores/" + S06 + "/permissions").cookie(bieb)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.mayUseAutomations").value(true));
        mvc.perform(get("/api/stores/" + S06).cookie(bieb)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.permissions.mayUseAutomations").value(true));
        UUID cleaning = jdbc.queryForObject("select r.cleaning_store_id from region r join store s on s.region_id = r.id"
                + " where s.id = ?", UUID.class, UUID.fromString(S06));
        var automation = mvc.perform(get("/api/stores/" + S06 + "/automation").cookie(bieb)).andExpect(status().isOk());
        if (cleaning == null) {
            automation.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .jsonPath("$.cleaningStore").doesNotExist());
        } else {
            automation.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .jsonPath("$.cleaningStore.id").value(cleaning.toString()));
        }
        mvc.perform(get("/api/users/me/pickups").cookie(bieb)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[*].mayAddExtraSlot").exists());
        mvc.perform(get("/api/stores/" + S06 + "/permissions").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.mayUseAutomations").value(false));
    }
}
