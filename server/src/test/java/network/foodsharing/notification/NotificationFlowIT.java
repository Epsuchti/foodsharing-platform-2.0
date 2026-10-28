package network.foodsharing.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.service.PickupSignupService;
import network.foodsharing.store.service.StoreTeamService;
import network.foodsharing.testsupport.SeedUsers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * N3/N4/A23/A24 on the seed (e2e profile, push in log mode): bell list and actions, settings, push subscription and
 * captured pushes, automation entry points of the pickup and store modules.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-flow-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@RecordApplicationEvents
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationFlowIT {

    static final String S07 = "/api/stores/00000020-0001-4000-8000-000000000007";
    static final UUID S07_ID = UUID.fromString("00000020-0001-4000-8000-000000000007");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PickupSignupService signups;

    @Autowired
    private StoreTeamService team;

    @Autowired
    private ApplicationEvents events;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @Order(1) // before the other tests add bells for foodsaver
    void bellListReadAndDelete() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/bells/unread-count").cookie(foodsaver)).andExpect(jsonPath("$.count").value(4));
        mvc.perform(get("/api/bells?size=5").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalItems").value(12))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.items[4].readAt").exists())
                .andExpect(jsonPath("$.items[0].titleKey").value(Matchers.startsWith("bell.")));
        String unread = "00000004-0002-4000-8000-000000000001";
        String foreign = "00000004-0002-4000-8000-000000000013";
        mvc.perform(post("/api/bells/read").cookie(foodsaver).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + unread + "\",\"" + foreign + "\"]}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/bells/unread-count").cookie(foodsaver)).andExpect(jsonPath("$.count").value(3));
        assertThat(count("select count(*) from bell where id = ? and read_at is null", UUID.fromString(foreign)))
                .isEqualTo(1);
        mvc.perform(delete("/api/bells/" + foreign).cookie(foodsaver).with(csrf())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BELL_NOT_FOUND"));
        mvc.perform(delete("/api/bells?readOnly=true").cookie(foodsaver).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/bells").cookie(foodsaver)).andExpect(jsonPath("$.page.totalItems").value(3));
        mvc.perform(post("/api/bells/read-all").cookie(foodsaver).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/bells?unreadOnly=true").cookie(foodsaver)).andExpect(jsonPath("$.page.totalItems").value(0));
        mvc.perform(get("/api/bells")).andExpect(status().isUnauthorized());
    }

    @Test
    void settingsDefaultsStoredRowsAndSystem() throws Exception {
        mvc.perform(get("/api/users/me/notification-settings").cookie(login(SeedUsers.SLEEPY)))
                .andExpect(jsonPath("$.categories[?(@.category == 'STORE_TEAM')].mail").value(Matchers.contains(false)))
                .andExpect(jsonPath("$.categories[?(@.category == 'SYSTEM')].mail").value(Matchers.contains(true)))
                .andExpect(jsonPath("$.pushAvailable").value(false))
                .andExpect(jsonPath("$.telegramBotName").value("foodsharing_dev_bot"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/users/me/notification-settings").cookie(foodsaver))
                .andExpect(jsonPath("$.pushAvailable").value(true))
                .andExpect(jsonPath("$.categories[?(@.category == 'FORUM')].mail").value(Matchers.contains(false)));
        mvc.perform(put("/api/users/me/notification-settings").cookie(foodsaver).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"categories\":[{\"category\":\"SYSTEM\","
                        + "\"bell\":true,\"mail\":false,\"push\":true,\"telegram\":true}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/users/me/notification-settings").cookie(foodsaver).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"categories\":[{\"category\":\"PICKUPS\","
                        + "\"bell\":true,\"mail\":false,\"push\":false,\"telegram\":true}],\"newsletterOptIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.category == 'PICKUPS')].push").value(Matchers.contains(false)))
                .andExpect(jsonPath("$.newsletterOptIn").value(true));
        assertThat(count("select count(*) from history_entry where action = 'NOTIFICATION_SETTINGS_CHANGED'"))
                .isEqualTo(1);
    }

    @Test
    void pushSubscriptionsAndCapturedPushes() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(delete("/api/dev/notifications")).andExpect(status().isNoContent());
        mvc.perform(get("/api/push/vapid-public-key").cookie(foodsaver)).andExpect(jsonPath("$.mode").value("LOG"))
                .andExpect(jsonPath("$.publicKey").value(Matchers.startsWith("BN")));
        String body = "{\"endpoint\":\"https://push.local/it-1\",\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}";
        for (int i = 0; i < 2; i++) { // upsert by endpoint
            mvc.perform(post("/api/push/subscriptions").cookie(foodsaver).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        }
        assertThat(count("select count(*) from push_subscription where user_id = ?", SeedUsers.FOODSAVER.id()))
                .isEqualTo(2);
        mvc.perform(post("/api/push/test").cookie(foodsaver).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/push/test").cookie(login(SeedUsers.SLEEPY)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_PUSH_SUBSCRIPTION"));

        // sign-up (event) → manager confirms → bell + one push per subscription
        Cookie welcome = login(SeedUsers.WELCOME);
        String pickup = S07 + "/pickups/2026-11-04T19:30:00Z";
        mvc.perform(post(pickup + "/signups").cookie(foodsaver).with(csrf())).andExpect(status().isOk());
        assertThat(events.stream(PickupEvents.PickupSignedUpEvent.class)
                .filter(e -> e.userId().equals(SeedUsers.FOODSAVER.id()))).hasSize(1);
        mvc.perform(post(pickup + "/signups/" + SeedUsers.FOODSAVER.id() + "/confirm").cookie(welcome).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/dev/notifications?channel=PUSH&type=PICKUP_CONFIRMED&userId=" + SeedUsers.FOODSAVER.id()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value(Matchers.not(Matchers.emptyString())));
        assertThat(count("select count(*) from bell where user_id = ? and type = 'PICKUP_CONFIRMED' "
                + "and pushed_at is not null and params like '%2026-11-04T19:30:00Z%'",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);

        mvc.perform(delete("/api/push/subscriptions").cookie(foodsaver).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://push.local/it-1\"}"))
                .andExpect(status().isNoContent());
        assertThat(count("select count(*) from push_subscription where user_id = ?", SeedUsers.FOODSAVER.id()))
                .isEqualTo(1);
    }

    @Test
    void automationEntryPoints() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String pickup = S07 + "/pickups/2026-11-11T19:30:00Z";
        Instant pickupAt = Instant.parse("2026-11-11T19:30:00Z");
        mvc.perform(post(pickup + "/signups").cookie(foodsaver).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));

        assertThat(signups.confirmByAutomation(S07_ID, pickupAt, SeedUsers.FOODSAVER.id(), "pickup-policy", null))
                .isTrue();
        assertThat(signups.confirmByAutomation(S07_ID, pickupAt, SeedUsers.FOODSAVER.id(), "pickup-policy", null))
                .isFalse();
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and confirmed = true "
                + "and confirmed_by is null", S07_ID, SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'SLOT_CONFIRMED' and actor_type = "
                + "'AUTOMATION' and secondary_subject_id = ?", SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'AUTO_CONFIRMED' and subject_id = ?",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(events.stream(PickupEvents.PickupConfirmedEvent.class).filter(e -> e.byUserId() == null))
                .hasSize(1);

        assertThat(signups.removeByAutomation(S07_ID, pickupAt, SeedUsers.FOODSAVER.id(), "pickup-policy",
                "Zu wenig Abstand zur letzten Abholung")).isTrue();
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ?",
                S07_ID, SeedUsers.FOODSAVER.id(), java.sql.Timestamp.from(pickupAt))).isZero();
        assertThat(count("select count(*) from bell where user_id = ? and type = 'PICKUP_AUTO_DECLINED' "
                + "and mail_outbox_id is not null", SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'AUTO_DECLINED' and reason = ?",
                "Zu wenig Abstand zur letzten Abholung")).isEqualTo(1);
        assertThat(events.stream(PickupEvents.PickupSignedOutEvent.class).filter(e -> e.byUserId() == null))
                .hasSize(1);

        // no open application → the store-team entry points do nothing
        assertThat(team.acceptByAutomation(S07_ID, SeedUsers.FOODSAVER.id(), "request-policy")).isFalse();
        assertThat(team.declineByAutomation(S07_ID, SeedUsers.FOODSAVER.id(), "request-policy", "zu weit")).isFalse();
    }
}
