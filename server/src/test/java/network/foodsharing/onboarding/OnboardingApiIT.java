package network.foodsharing.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Arrays;
import network.foodsharing.config.MutableClock;
import network.foodsharing.onboarding.service.OnboardingNudgeService;
import network.foodsharing.testsupport.FixedClockConfig;
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

/** Journey per seed stage, learning progress, region steps, explainer and nudges (e2e profile, seed). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:onboarding-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class OnboardingApiIT {

    private static final String ZURICH = "00000010-0001-4000-8000-000000000040";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private OnboardingNudgeService nudges;

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
    void everySeedStageShowsItsCurrentStep() throws Exception {
        mvc.perform(get("/api/onboarding/journey").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStep").value("PROFILE_REGION"))
                .andExpect(jsonPath("$.steps[0].status").value("DONE"))
                .andExpect(jsonPath("$.steps[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.steps[2].status").value("LOCKED"))
                .andExpect(jsonPath("$.tracks.length()").value(4));
        mvc.perform(get("/api/onboarding/journey").cookie(login(SeedUsers.QUIZ)))
                .andExpect(jsonPath("$.steps[?(@.key == 'LEARN')].status").value("DONE"))
                .andExpect(jsonPath("$.currentStep").value(Matchers.oneOf("QUIZ", "AGREEMENT")));
        mvc.perform(get("/api/onboarding/journey").cookie(login(SeedUsers.PENDING)))
                .andExpect(jsonPath("$.currentStep").value("ID_CHECK"))
                .andExpect(jsonPath("$.extraSteps[0].key").value("newcomer-meeting"))
                .andExpect(jsonPath("$.extraSteps[0].status").value("DONE"))
                .andExpect(jsonPath("$.steps[?(@.key == 'ID_CHECK')].status").value("WAITING_FOR_OTHERS"))
                .andExpect(jsonPath("$.steps[?(@.key == 'ID_CHECK')].waitingFor.contacts[0].id").isNotEmpty())
                .andExpect(jsonPath("$.steps[?(@.key == 'INTRO_PICKUPS')].progress.done").value(3))
                .andExpect(jsonPath("$.region.id").value(ZURICH));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/onboarding/journey").cookie(foodsaver))
                .andExpect(jsonPath("$.complete").value(true));
        mvc.perform(get("/api/onboarding/journey").cookie(foodsaver)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'JOURNEY_COMPLETED' "
                + "and subject_id = ?", Integer.class, SeedUsers.FOODSAVER.id())).isEqualTo(1);
        mvc.perform(get("/api/auth/session").cookie(foodsaver)).andExpect(jsonPath("$.onboarding.complete").value(true));
        mvc.perform(get("/api/auth/session").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(jsonPath("$.onboarding.complete").value(false))
                .andExpect(jsonPath("$.onboarding.currentStep").value("PROFILE_REGION"));
    }

    @Test
    void learningProgressAndRegionSteps() throws Exception {
        Cookie profile = login(SeedUsers.PROFILE);
        mvc.perform(json(post("/api/onboarding/learning/nope/read").cookie(profile), ""))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));
        mvc.perform(json(post("/api/onboarding/learning/rules/read").cookie(profile), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[?(@.key == 'LEARN')].progress.done").value(1));
        mvc.perform(json(post("/api/onboarding/learning/rules/read").cookie(profile), "")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from learning_progress where user_id = ?", Integer.class,
                SeedUsers.PROFILE.id())).isEqualTo(1);

        String url = "/api/onboarding/regions/" + ZURICH + "/users/" + SeedUsers.EINAB1.id()
                + "/steps/newcomer-meeting/confirm";
        mvc.perform(json(post(url).cookie(login(SeedUsers.FOODSAVER)), "")).andExpect(status().isForbidden());
        Cookie welcome = login(SeedUsers.WELCOME);
        mvc.perform(json(post(url).cookie(welcome), "")).andExpect(status().isNoContent());
        mvc.perform(json(post(url).cookie(welcome), "")).andExpect(status().isNoContent());
        mvc.perform(json(post("/api/onboarding/regions/" + ZURICH + "/users/" + SeedUsers.EINAB1.id()
                + "/steps/unknown/confirm").cookie(welcome), "")).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/onboarding/regions/" + ZURICH + "/users/" + SeedUsers.NEWBIE.id()
                        + "/steps/newcomer-meeting/confirm").cookie(welcome), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'REGION_STEP_CONFIRMED' "
                + "and subject_id = ?", Integer.class, SeedUsers.EINAB1.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? "
                + "and type = 'ONBOARDING_STEP_UNBLOCKED'", Integer.class, SeedUsers.EINAB1.id())).isEqualTo(1);
    }

    @Test
    void explainerWithCountersPreviewAndRegion() throws Exception {
        mvc.perform(get("/api/public/onboarding/explainer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counters.foodsavers").value(Matchers.greaterThanOrEqualTo(9)))
                .andExpect(jsonPath("$.counters.regions").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.counters.stores").value(0))
                .andExpect(jsonPath("$.journeyPreview.length()").value(8))
                .andExpect(jsonPath("$.journeyPreview[5].key").value("INTRO_PICKUPS"))
                .andExpect(jsonPath("$.region").doesNotExist());
        mvc.perform(get("/api/public/onboarding/explainer").param("regionId", ZURICH))
                .andExpect(jsonPath("$.region.slug").value("zuerich-stadt"));
        mvc.perform(get("/api/public/onboarding/explainer").param("regionId", "00000010-0001-4000-8000-999999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stalledNewcomersAreNudgedOncePer30Days() throws Exception {
        try {
            clock.fixAt(FixedClockConfig.SEED_NOW.plus(Duration.ofDays(40)));
            int sent = nudges.sendStalledNudges();
            assertThat(sent).isGreaterThan(0);
            assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = "
                    + "'ONBOARDING_STALLED'", Integer.class, SeedUsers.NEWBIE.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from mail_outbox where template = "
                    + "'onboarding/stalled'", Integer.class)).isEqualTo(sent);
            assertThat(nudges.sendStalledNudges()).isZero();
        } finally {
            clock.fixAt(FixedClockConfig.SEED_NOW);
        }
    }
}
