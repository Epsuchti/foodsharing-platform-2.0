package network.foodsharing.pickup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
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

/**
 * PU10 + EA16 move sync (decision 170) end to end on the seed (e2e profile, clock at SEED_NOW 2026-11-02T09:00Z): the
 * Migros manager moves the regular Friday pickup the guide is signed up for (with the seeded linked EinAb booked by
 * pending@local). Rights, guards, the cancelled occurrence + one-time pickup, sign-ups that keep their confirmation,
 * PICKUP_MOVED to the signed-up guide (not the mover), the linked EinAb moving along with INTRO_PICKUP_MOVED to the
 * newcomer. Own database, because the steps change the seed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickup-move-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class PickupMoveIT {

    private static final String MIGROS = "/api/stores/00000020-0001-4000-8000-000000000003";
    private static final String FROM = "2026-11-06T17:30:00Z";
    private static final String TO = "2026-11-07T10:00:00Z";
    /** Seeded linked EinAb of guide@local on his confirmed Migros pickup, booked by pending@local. */
    private static final UUID SEEDED = UUID.fromString("00000030-0001-4000-8000-000000000028");

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

    private org.springframework.test.web.servlet.ResultActions move(Cookie who, String from, String body)
            throws Exception {
        return mvc.perform(post(MIGROS + "/pickups/" + from + "/move").cookie(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int bells(SeedUsers user, String type) {
        return count("select count(*) from bell where user_id = ? and type = ?", user.id(), type);
    }

    @Test
    void aManagerMovesAPickupSignupsAndTheLinkedEinAbMoveAlong() throws Exception {
        Cookie manager = login(SeedUsers.BOT);
        Cookie member = login(SeedUsers.FOODSAVER);

        // rights and guards: nothing changes
        move(member, FROM, "{\"newPickupAt\":\"" + TO + "\"}").andExpect(status().isForbidden());
        move(manager, FROM, "{\"newPickupAt\":\"2026-11-10T17:30:00Z\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PICKUP_TIME_TAKEN"));
        move(manager, FROM, "{\"newPickupAt\":\"2026-11-01T10:00:00Z\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PICKUP_IN_PAST"));
        move(manager, FROM, "{\"newPickupAt\":\"" + FROM + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        move(manager, "2026-11-06T17:31:00Z", "{\"newPickupAt\":\"" + TO + "\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PICKUP_NOT_FOUND"));
        move(manager, FROM, "{\"newPickupAt\":\"" + TO + "\",\"message\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from history_entry where action = 'PICKUP_MOVED'")).isZero();

        int guideBells = bells(SeedUsers.GUIDE, "PICKUP_MOVED");
        move(manager, FROM, "{\"newPickupAt\":\"" + TO + "\",\"message\":\"Der Laden schliesst früher.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.pickupAt").value(TO))
                .andExpect(jsonPath("$.slotCount").value(3)).andExpect(jsonPath("$.isRegular").value(false))
                .andExpect(jsonPath("$.occupiedSlots[0].user.id").value(SeedUsers.GUIDE.id().toString()))
                .andExpect(jsonPath("$.occupiedSlots[0].confirmed").value(true));

        // the regular occurrence stays cancelled; the list shows the pickup at the new time only
        String list = mvc.perform(get(MIGROS + "/pickups").cookie(manager)).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$[*].pickupAt")).contains(TO).doesNotContain(FROM);
        assertThat(count("select slot_count from pickup where store_id = ? and pickup_at = ?",
                UUID.fromString(MIGROS.substring(12)), java.sql.Timestamp.from(java.time.Instant.parse(FROM))))
                .isZero();
        assertThat(count("select count(*) from pickup_signup where user_id = ? and pickup_at = ? and confirmed = true "
                        + "and reminded_at is null", SeedUsers.GUIDE.id(),
                java.sql.Timestamp.from(java.time.Instant.parse(TO)))).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'PICKUP_MOVED' and metadata like ?",
                "%\"to\":\"" + TO + "\"%")).isEqualTo(1);

        // PICKUP_MOVED to the signed-up guide with the message, not to the mover
        assertThat(bells(SeedUsers.GUIDE, "PICKUP_MOVED")).isEqualTo(guideBells + 1);
        assertThat(bells(SeedUsers.BOT, "PICKUP_MOVED")).isZero();
        assertThat(count("select count(*) from mail_outbox where template = 'pickup/moved' and text_body like ?",
                "%Der Laden schliesst früher.%")).isEqualTo(1);

        // EA16: the linked EinAb moved along, the booking stays, the newcomer is told with old and new time
        assertThat(jdbc.queryForObject("select start_at from intro_pickup where id = ?", java.sql.Timestamp.class,
                SEEDED).toInstant()).isEqualTo(java.time.Instant.parse(TO));
        assertThat(jdbc.queryForObject("select linked_pickup_at from intro_pickup where id = ?",
                java.sql.Timestamp.class, SEEDED).toInstant()).isEqualTo(java.time.Instant.parse(TO));
        assertThat(count("select count(*) from intro_pickup_slot where intro_pickup_id = ? and status = 'BOOKED'",
                SEEDED)).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'UPDATED' and subject_id = ? "
                + "and metadata like '%STORE_PICKUP_MOVED%'", SEEDED)).isEqualTo(1);
        assertThat(bells(SeedUsers.PENDING, "INTRO_PICKUP_MOVED")).isEqualTo(1);
        assertThat(bells(SeedUsers.GUIDE, "INTRO_PICKUP_MOVED")).isZero();
        assertThat(count("select count(*) from mail_outbox where template = 'intropickup/moved'")).isEqualTo(1);
        // the guide's sign-out warning follows the EinAb to the new time
        String guideList = mvc.perform(get(MIGROS + "/pickups").cookie(login(SeedUsers.GUIDE))).andReturn()
                .getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(guideList, "$[?(@.pickupAt == '" + TO + "')].occupiedSlots[?(@.user.id"
                + " == '" + SeedUsers.GUIDE.id() + "')].linkedIntroPickup.introPickupId"))
                .containsExactly(SEEDED.toString());
    }
}
