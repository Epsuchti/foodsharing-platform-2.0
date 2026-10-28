package network.foodsharing.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
}
