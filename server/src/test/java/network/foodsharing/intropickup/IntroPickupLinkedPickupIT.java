package network.foodsharing.intropickup;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * EA16 (decision 169) end to end on the seed (e2e profile, clock at SEED_NOW 2026-11-02T09:00Z): the guide's
 * confirmed store pickups as EinAb candidates, linked creates, the unlink rule, no reassign, the sign-out warning data
 * and the cancel sync over every removal path (sign-out, leaving the team, deleting all future pickups). Own database,
 * because the steps change the seed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:intro-pickup-linked-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class IntroPickupLinkedPickupIT {

    private static final String ZURICH = "00000010-0001-4000-8000-000000000040";
    private static final String MIGROS = "00000020-0001-4000-8000-000000000003";
    private static final String TINY_FISH = "00000020-0001-4000-8000-000000000009";
    /** Seeded linked EinAb of guide@local on his confirmed Migros pickup, booked by pending@local. */
    private static final String SEEDED = "00000030-0001-4000-8000-000000000028";
    private static final String SEEDED_AT = "2026-11-06T17:30:00Z";

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

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    private static String create(String storeId, String at, String linkedAt) {
        return "{\"category\":\"TAKEOUT\",\"startAt\":\"" + at + "\",\"slotCount\":2,\"visitFairteiler\":false,"
                + "\"businessName\":\"Tiny Fish Stadelhofen\",\"storeId\":\"" + storeId + "\","
                + "\"privateAddress\":\"Stadelhoferstrasse 10, 8001 Zürich\",\"publicGeneralLocation\":\"Zürich\""
                + (linkedAt == null ? "" : ",\"linkedPickupAt\":\"" + linkedAt + "\"") + "}";
    }

    private boolean deleted(String introPickupId) {
        return jdbc.queryForObject("select deleted_at is not null from intro_pickup where id = ?", Boolean.class,
                UUID.fromString(introPickupId));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int autoCancelled(String introPickupId, String cause) {
        return count("select count(*) from history_entry where action = 'AUTO_CANCELLED' and subject_id = ? "
                + "and metadata like ?", UUID.fromString(introPickupId), "%\"cause\":\"" + cause + "\"%");
    }

    @Test
    void ownStorePickupsBecomeLinkedEinAbsThatAreCancelledWithTheSignup() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        String guideId = SeedUsers.GUIDE.id().toString();

        // the old iCal import is gone
        mvc.perform(get("/api/guide/ical-candidates").cookie(guide)).andExpect(status().isNotFound());

        // candidates: confirmed future sign-ups only (the unconfirmed Migros 11-10 is not one), the seeded linked
        // Migros 11-06 is used already
        String candidates = body(get("/api/guide/pickup-candidates").cookie(guide));
        assertThat(JsonPath.<List<String>>read(candidates, "$[*].startAt"))
                .containsExactly("2026-11-06T19:00:00Z", "2026-11-13T19:00:00Z");
        assertThat(JsonPath.<List<String>>read(candidates, "$[*].storeId")).containsOnly(TINY_FISH);
        assertThat(JsonPath.<String>read(candidates, "$[0].storeName")).isEqualTo("Tiny Fish Stadelhofen");
        assertThat(JsonPath.<String>read(candidates, "$[0].address")).isEqualTo("Stadelhoferstrasse 10, 8001 Zürich");
        assertThat(JsonPath.<String>read(candidates, "$[0].city")).isEqualTo("Zürich");
        assertThat(JsonPath.<String>read(candidates, "$[0].category")).isEqualTo("TAKEOUT");
        assertThat(JsonPath.<String>read(candidates, "$[0].regionId")).isEqualTo(ZURICH);
        // other users get no candidates of the guide; foodsharers are no guides at all
        mvc.perform(get("/api/guide/pickup-candidates").cookie(login(SeedUsers.EINAB1)))
                .andExpect(status().isForbidden());

        // linked creates: A and B on the two Tiny Fish pickups; the candidate list empties
        String a = JsonPath.read(body(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                create(TINY_FISH, "2026-11-06T19:00:00Z", "2026-11-06T19:00:00Z"))), "$.id");
        mvc.perform(get("/api/intro-pickups/" + a).cookie(guide))
                .andExpect(jsonPath("$.linkedPickupAt").value("2026-11-06T19:00:00Z"));
        String b = JsonPath.read(body(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                create(TINY_FISH, "2026-11-13T19:00:00Z", "2026-11-13T19:00:00Z"))), "$.id");
        mvc.perform(get("/api/guide/pickup-candidates").cookie(guide)).andExpect(jsonPath("$.length()").value(0));

        // no confirmed sign-up → 409; a start that differs from the pickup stores no link (and needs no sign-up)
        mvc.perform(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                        create(MIGROS, "2026-11-10T17:30:00Z", "2026-11-10T17:30:00Z")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_PICKUP_NOT_SIGNED_UP"));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                        create(TINY_FISH, "2026-11-20T19:00:00Z", "2026-11-13T19:00:00Z")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.linkedPickupAt").doesNotExist());

        // a linked EinAb cannot be handed to another guide
        mvc.perform(json(put("/api/intro-pickups/" + a + "/guide").cookie(guide),
                        "{\"guideId\":\"" + SeedUsers.BOT.id() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINKED_INTRO_PICKUP_NOT_REASSIGNABLE"));

        // other fields keep the link; changing the start removes it (history unlinked=true)
        mvc.perform(json(patch("/api/intro-pickups/" + a).cookie(guide), "{\"publicInfo\":\"Bitte Taschen\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedPickupAt").value("2026-11-06T19:00:00Z"));
        mvc.perform(json(patch("/api/intro-pickups/" + a).cookie(guide), "{\"startAt\":\"2026-11-06T18:30:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedPickupAt").doesNotExist());
        assertThat(count("select count(*) from history_entry where action = 'UPDATED' and subject_id = ? "
                + "and metadata like '%\"unlinked\":true%'", UUID.fromString(a))).isEqualTo(1);

        // sign-out warning data: the guide's own slot and the store manager see the linked EinAb, others do not
        String slotPath = "$[?(@.pickupAt == '" + SEEDED_AT + "')].occupiedSlots[?(@.user.id == '" + guideId
                + "')].linkedIntroPickup";
        String own = body(get("/api/stores/" + MIGROS + "/pickups").cookie(guide));
        assertThat(JsonPath.<List<String>>read(own, slotPath + ".introPickupId")).containsExactly(SEEDED);
        assertThat(JsonPath.<List<Integer>>read(own, slotPath + ".bookedCount")).containsExactly(1);
        String manager = body(get("/api/stores/" + MIGROS + "/pickups").cookie(login(SeedUsers.BOT)));
        assertThat(JsonPath.<List<String>>read(manager, slotPath + ".introPickupId")).containsExactly(SEEDED);
        String member = body(get("/api/stores/" + MIGROS + "/pickups").cookie(login(SeedUsers.FOODSAVER)));
        assertThat(JsonPath.<List<Object>>read(member, slotPath + ".introPickupId")).isEmpty();

        // 1. the guide signs out: the seeded EinAb is cancelled, booked or not; the newcomer and the guide are told
        int newcomerBells = count("select count(*) from bell where user_id = ? and type = "
                + "'INTRO_PICKUP_CANCELLED_BY_GUIDE'", SeedUsers.PENDING.id());
        mvc.perform(json(delete("/api/stores/" + MIGROS + "/pickups/" + SEEDED_AT + "/signups/" + guideId)
                .cookie(guide), "{}")).andExpect(status().isNoContent());
        assertThat(deleted(SEEDED)).isTrue();
        assertThat(count("select count(*) from intro_pickup_slot where intro_pickup_id = ? and status <> 'CANCELLED'",
                UUID.fromString(SEEDED))).isZero();
        assertThat(autoCancelled(SEEDED, "SIGNED_OUT")).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'INTRO_PICKUP_CANCELLED_BY_GUIDE'",
                SeedUsers.PENDING.id())).isEqualTo(newcomerBells + 1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'INTRO_PICKUP_AUTO_CANCELLED'",
                SeedUsers.GUIDE.id())).isEqualTo(1);
        assertThat(count("select count(*) from mail_outbox where template = 'intropickup/cancelled-by-guide'"))
                .isPositive();
        // the newcomer's booking no longer counts or shows as upcoming
        mvc.perform(get("/api/users/me/intro-pickups").cookie(login(SeedUsers.PENDING)))
                .andExpect(jsonPath("$[?(@.introPickup.id == '" + SEEDED + "')]").isEmpty());
        // the unlinked A is untouched by anything that follows
        assertThat(deleted(a)).isFalse();

        // 2. leaving the Tiny Fish team deletes the future sign-ups and cancels B
        mvc.perform(json(delete("/api/stores/" + TINY_FISH + "/team/" + guideId).cookie(guide),
                "{\"message\":\"Ich ziehe um, danke!\"}")).andExpect(status().isNoContent());
        assertThat(deleted(b)).isTrue();
        assertThat(autoCancelled(b, "LEFT_TEAM")).isEqualTo(1);

        // 3. "delete all future pickups": the manager confirms the Migros 11-10 sign-up, the guide links an EinAb to
        //    it, then signs out of everything
        String migros1110 = "/api/stores/" + MIGROS + "/pickups/2026-11-10T17:30:00Z/signups/" + guideId;
        mvc.perform(post(migros1110 + "/confirm").cookie(login(SeedUsers.BOT)).with(csrf()))
                .andExpect(status().isNoContent());
        String c = JsonPath.read(body(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                create(MIGROS, "2026-11-10T17:30:00Z", "2026-11-10T17:30:00Z"))), "$.id");
        mvc.perform(json(delete("/api/users/" + guideId + "/pickups").cookie(guide), "{\"reason\":\"Ferien\"}"))
                .andExpect(status().isNoContent());
        assertThat(deleted(c)).isTrue();
        assertThat(autoCancelled(c, "ALL_FUTURE_DELETED")).isEqualTo(1);
        assertThat(deleted(a)).isFalse();
    }
}
