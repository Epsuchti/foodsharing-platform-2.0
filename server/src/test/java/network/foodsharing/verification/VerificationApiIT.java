package network.foodsharing.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Verification queue, verify / unverify, identity, passports and wallets end to end (e2e profile, seed). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:verification-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class VerificationApiIT {

    private static final String ZURICH = "00000010-0001-4000-8000-000000000040";

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
    void queueVerifyAndUnverify() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZURICH + "/verification/queue").cookie(bot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].user.id").value(Matchers.contains(SeedUsers.PENDING.id().toString())))
                .andExpect(jsonPath("$.items[0].journey.introPickupsDone").value(3))
                .andExpect(jsonPath("$.items[0].journey.fairteilerDone").value(true))
                .andExpect(jsonPath("$.items[0].guideComments").isNotEmpty());
        mvc.perform(get("/api/regions/" + ZURICH + "/verification/queue").param("filter", "ALL_UNVERIFIED")
                        .param("size", "50").cookie(bot))
                .andExpect(jsonPath("$.items[*].user.id").value(Matchers.hasItems(SeedUsers.EINAB1.id().toString(),
                        SeedUsers.PENDING.id().toString())));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZURICH + "/verification/queue").cookie(foodsaver))
                .andExpect(status().isForbidden());

        String url = "/api/users/" + SeedUsers.PENDING.id() + "/verification";
        mvc.perform(json(post(url).cookie(foodsaver), "{\"message\":\"Alles bestens erledigt\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post(url).cookie(bot), "{\"message\":\"kurz\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post(url).cookie(bot), "{\"message\":\"Drei EinAbs, Ausweis geprüft\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(SeedUsers.PENDING.id().toString()));
        mvc.perform(json(post(url).cookie(bot), "{\"message\":\"Drei EinAbs, Ausweis geprüft\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
        assertThat(jdbc.queryForObject("select verified from user_account where id = ?", Boolean.class,
                SeedUsers.PENDING.id())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'USER_VERIFIED'",
                Integer.class, SeedUsers.PENDING.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_note where user_id = ? "
                + "and kind = 'VERIFICATION_MESSAGE'", Integer.class, SeedUsers.PENDING.id())).isEqualTo(1);
        mvc.perform(get("/api/users/" + SeedUsers.PENDING.id() + "/verification/history").cookie(bot))
                .andExpect(jsonPath("$.items[0].action").value("VERIFIED"))
                .andExpect(jsonPath("$.items[0].reason").value("Drei EinAbs, Ausweis geprüft"));

        mvc.perform(json(delete(url).cookie(bot), "{\"message\":\"Versehentlich verifiziert\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(delete("/api/users/" + SeedUsers.EINAB1.id() + "/verification").cookie(bot),
                        "{\"message\":\"Ist gar nicht verifiziert\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_VERIFIED"));
    }

    @Test
    void identityConfirmation() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String url = "/api/users/" + SeedUsers.EINAB1.id() + "/identity";
        mvc.perform(get(url).cookie(bot)).andExpect(jsonPath("$.confirmed").value(false));
        mvc.perform(json(post("/api/users/" + SeedUsers.BOT.id() + "/identity").cookie(bot), ""))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_CONFIRM_OWN_IDENTITY"));
        mvc.perform(json(post(url).cookie(bot), "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.confirmedBy.id").value(SeedUsers.BOT.id().toString()));
        mvc.perform(json(post(url).cookie(bot), "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_CONFIRMED"));
        Cookie einab1 = login(SeedUsers.EINAB1);
        mvc.perform(get(url).cookie(einab1)).andExpect(jsonPath("$.confirmed").value(true));
        mvc.perform(json(delete(url).cookie(bot), "{\"reason\":\"Falsch\"}")).andExpect(status().isForbidden());
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(json(delete(url).cookie(orga), "{\"reason\":\"Falsche Person\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));
        mvc.perform(json(delete(url).cookie(orga), "{\"reason\":\"Nochmals\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_NOT_CONFIRMED"));
    }

    @Test
    void passportsAndWallets() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/users/me/passport").cookie(foodsaver))
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.validUntil").value("2028-03-01"))
                .andExpect(jsonPath("$.requestPending").value(true))
                .andExpect(jsonPath("$.walletApple").value("NOT_CONFIGURED"));
        mvc.perform(get("/api/users/me/passport.pdf").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(json(post("/api/users/me/passport-requests").cookie(foodsaver), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASSPORT_REQUEST_PENDING"));
        mvc.perform(get("/api/users/me/wallets/apple").cookie(foodsaver)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_CONFIGURED"));
        mvc.perform(get("/api/users/me/wallets/google").cookie(foodsaver)).andExpect(status().isServiceUnavailable());

        Cookie sleepy = login(SeedUsers.SLEEPY);
        mvc.perform(get("/api/users/me/passport").cookie(sleepy)).andExpect(jsonPath("$.valid").value(false));
        mvc.perform(get("/api/users/me/passport.pdf").cookie(sleepy)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSPORT_NOT_VALID"));
        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(get("/api/users/me/passport.pdf").cookie(newbie)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_VERIFIED"));
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(post("/api/users/me/passport-requests").cookie(bieb), "")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestPending").value(true));

        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZURICH + "/passport-requests").cookie(bot))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/passports").cookie(bot),
                        "{\"userIds\":[\"" + SeedUsers.NEWBIE.id() + "\"],\"createPdf\":true,\"renew\":true,"
                                + "\"paperSize\":\"A4\",\"notifyUsers\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USERS_NOT_IN_REGION"));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/passports").cookie(foodsaver),
                        "{\"userIds\":[\"" + SeedUsers.SLEEPY.id() + "\"],\"createPdf\":true,\"renew\":true,"
                                + "\"paperSize\":\"A4\",\"notifyUsers\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/regions/" + ZURICH + "/passports").cookie(bot),
                        "{\"userIds\":[\"" + SeedUsers.SLEEPY.id() + "\",\"" + SeedUsers.FOODSAVER.id()
                                + "\"],\"createPdf\":true,\"renew\":true,\"paperSize\":\"A4\",\"notifyUsers\":true}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").isNotEmpty());
        mvc.perform(get("/api/users/me/passport").cookie(sleepy)).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.validFrom").value("2026-11-02"))
                .andExpect(jsonPath("$.validUntil").value("2029-11-02"));
        mvc.perform(get("/api/users/me/passport").cookie(foodsaver))
                .andExpect(jsonPath("$.requestPending").value(false));
        mvc.perform(get("/api/regions/" + ZURICH + "/passport-requests").param("includeFulfilled", "true").cookie(bot))
                .andExpect(jsonPath("$[?(@.user.id == '" + SeedUsers.FOODSAVER.id() + "')].fulfilledBy.id")
                        .value(SeedUsers.BOT.id().toString()));
        assertThat(jdbc.queryForObject("select count(*) from bell where type = 'PASSPORT_CREATED'", Integer.class))
                .isEqualTo(2);
        mvc.perform(get("/api/users/" + SeedUsers.SLEEPY.id() + "/passport-history").cookie(sleepy))
                .andExpect(jsonPath("$.items[0].action").value("PASSPORT_GENERATED_BY_AMBASSADOR"));
        mvc.perform(get("/api/users/" + SeedUsers.SLEEPY.id() + "/passport-history").cookie(newbie))
                .andExpect(status().isForbidden());
    }
}
