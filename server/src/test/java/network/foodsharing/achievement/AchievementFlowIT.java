package network.foodsharing.achievement;

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
import java.util.List;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/** AC1 end to end: seeded visibility, define/award/revoke, self/non-member denials, auto award, profile SPI. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:achievements-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class AchievementFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String HYGIENE = "00000051-0001-4000-8000-000000000001";
    private static final String PUTZHELD = "00000051-0001-4000-8000-000000000003";
    private static final String SOMMERFEST = "00000051-0001-4000-8000-000000000004";
    private static final String HIDDEN = "00000051-0001-4000-8000-000000000005";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate tx;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Cookie cookie,
                                                      String body) {
        return builder.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private List<String> ids(Cookie viewer, SeedUsers user) throws Exception {
        String body = mvc.perform(get("/api/users/" + user.id() + "/achievements").cookie(viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].achievementId");
    }

    @Test
    void seededVisibilityPerViewer() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        Cookie bieb = login(SeedUsers.BIEB);
        Cookie bot = login(SeedUsers.BOT);
        assertThat(ids(jumper, SeedUsers.FOODSAVER)).contains(HYGIENE, PUTZHELD);
        // STORE_MANAGERS: not for a foodsaver, but for a store manager of the region and the recipient
        assertThat(ids(foodsaver, SeedUsers.JUMPER)).doesNotContain(SOMMERFEST);
        assertThat(ids(bieb, SeedUsers.JUMPER)).contains(SOMMERFEST);
        assertThat(ids(jumper, SeedUsers.JUMPER)).contains(SOMMERFEST);
        // HIDDEN: only administrators, not even the recipient
        assertThat(ids(jumper, SeedUsers.GUIDE)).doesNotContain(HIDDEN);
        assertThat(ids(login(SeedUsers.GUIDE), SeedUsers.GUIDE)).doesNotContain(HIDDEN);
        assertThat(ids(bot, SeedUsers.GUIDE)).contains(HIDDEN);
        // region list: members do not see the hidden definition
        mvc.perform(get("/api/achievements").param("regionId", ZUERICH).cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/achievements").param("regionId", ZUERICH).cookie(bot)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].mayAdminister").value(true));
        mvc.perform(get("/api/achievements/" + HIDDEN).cookie(jumper)).andExpect(status().isForbidden());
        mvc.perform(get("/api/achievements").param("regionId", ZUERICH).cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isForbidden());
        // profile SPI
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/profile").cookie(jumper))
                .andExpect(status().isOk()).andExpect(jsonPath("$.achievements.length()").value(2));
    }

    @Test
    void defineAwardEditRevoke() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String created = mvc.perform(json(post("/api/achievements"), bot, "{\"regionId\":\"" + ZUERICH + "\","
                        + "\"name\":\"Retter des Monats\",\"icon\":\"star\",\"visibility\":\"SCOPE\","
                        + "\"duplicateMode\":\"OVERRIDE\",\"validityDays\":30,"
                        + "\"translations\":{\"fr\":{\"name\":\"Sauveur du mois\"}}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.translations.fr.name").value("Sauveur du mois"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(json(post("/api/achievements/" + id + "/awards"), bot,
                "{\"userId\":\"" + SeedUsers.BOT.id() + "\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(json(post("/api/achievements/" + id + "/awards"), bot,
                "{\"userId\":\"" + SeedUsers.NEWBIE.id() + "\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACHIEVEMENT_RECIPIENT_NOT_MEMBER"));
        String award = mvc.perform(json(post("/api/achievements/" + id + "/awards"), bot,
                        "{\"userId\":\"" + SeedUsers.FOODSAVER.id() + "\",\"notice\":\"Oktober\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.validUntil").exists())
                .andExpect(jsonPath("$.awardedBy.id").value(SeedUsers.BOT.id().toString()))
                .andReturn().getResponse().getContentAsString();
        String awardId = JsonPath.read(award, "$.id");
        // OVERRIDE: a second award extends the first one
        mvc.perform(json(post("/api/achievements/" + id + "/awards"), bot,
                "{\"userId\":\"" + SeedUsers.FOODSAVER.id() + "\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(awardId));
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'ACHIEVEMENT_AWARDED'",
                Integer.class, SeedUsers.FOODSAVER.id())).isGreaterThanOrEqualTo(1);
        mvc.perform(json(patch("/api/achievements/awards/" + awardId), bot, "{\"validUntil\":\"2030-01-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validUntil").value("2030-01-01"));
        mvc.perform(get("/api/achievements/" + id + "/awards").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/achievements/awards/" + awardId).with(csrf()).cookie(bot))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/achievements/" + id + "/awards").cookie(bot)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revokedAt").exists());
        mvc.perform(delete("/api/achievements/" + id).with(csrf()).cookie(bot)).andExpect(status().isNoContent());
        mvc.perform(get("/api/achievements/" + id).cookie(bot)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where subject_id = ?", Integer.class,
                java.util.UUID.fromString(id))).isGreaterThanOrEqualTo(5);
        // global achievements: ORGA only; system achievements are protected
        mvc.perform(delete("/api/achievements/" + HYGIENE).with(csrf()).cookie(bot)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/achievements/" + HYGIENE).with(csrf()).cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACHIEVEMENT_SYSTEM_PROTECTED"));
    }

    @Test
    void passedHygieneQuizAwardsTheCertificate() throws Exception {
        tx.executeWithoutResult(status -> events.publishEvent(
                new QuizSessionService.QuizPassed(SeedUsers.QUIZ.id(), QuizKind.HYGIENE)));
        assertThat(jdbc.queryForObject("select count(*) from awarded_achievement where user_id = ?"
                + " and achievement_id = ? and revoked_at is null", Integer.class, SeedUsers.QUIZ.id(),
                java.util.UUID.fromString(HYGIENE))).isEqualTo(1);
        assertThat(ids(login(SeedUsers.QUIZ), SeedUsers.QUIZ)).contains(HYGIENE);
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'AUTO_AWARDED'"
                + " and secondary_subject_id = ? and actor_type = 'SYSTEM'", Integer.class, SeedUsers.QUIZ.id()))
                .isEqualTo(1);
    }
}
