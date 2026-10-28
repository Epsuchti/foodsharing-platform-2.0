package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A13/A21/A40: GDPR export, no-automatic-delete flag, self and ORGA deletion with anonymisation and archive. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deletion-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class AccountDeletionIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<AccountDeletionContributor> deletionContributors;

    @Autowired
    private List<DataExportContributor> exportContributors;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void exportContainsEverySection() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String body = mvc.perform(post("/api/users/me/export").with(csrf()).cookie(bieb))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.jobId");
        byte[] zip = mvc.perform(get("/api/jobs/" + jobId + "/result").cookie(bieb)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertThat(names).contains("profile.json", "settings.json", "history.json", "sessions.json",
                "notifications.json", "regions.json", "stores.json", "pickups.json", "community.json",
                "calendar.json", "events.json", "mailboxes.json", "messages.json", "forum.json", "polls.json",
                "surveys.json", "quiz.json", "onboarding.json", "verification.json", "intropickups.json",
                "automation.json", "baskets.json", "reports.json", "achievements.json")
                .anyMatch(name -> name.startsWith("uploads/") && name.endsWith("avatar-bieb.png"));
        mvc.perform(post("/api/users/me/export").with(csrf()).cookie(bieb)).andExpect(status().isTooManyRequests());
    }

    /** Every module that holds personal data takes part in both the deletion and the export (A40). */
    @Test
    void everyPersonalDataModuleContributes() {
        Set<String> expected = Set.of("auth", "notification", "region", "store", "pickup", "mailbox", "event",
                "calendar", "profile", "messaging", "forum", "poll", "survey", "quiz", "onboarding", "verification",
                "intropickup", "automation", "basket", "report", "achievement");
        assertThat(modules(deletionContributors)).containsAll(expected);
        assertThat(modules(exportContributors)).containsAll(expected);
    }

    private static Set<String> modules(List<?> beans) {
        return beans.stream().map(bean -> org.springframework.aop.support.AopUtils.getTargetClass(bean).getPackageName()
                .split("\\.")[2]).collect(Collectors.toSet());
    }

    @Test
    void selfDeletionAnonymisesAndKeepsAuthoredContent() throws Exception {
        String id = SeedUsers.FOODSAVER.id().toString();
        mvc.perform(patch("/api/admin/users/" + id + "/no-automatic-delete").with(csrf())
                        .cookie(login(SeedUsers.JUMPER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + id + "/no-automatic-delete").with(csrf())
                        .cookie(login(SeedUsers.ORGA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select no_automatic_delete from user_account where id = ?", Boolean.class,
                SeedUsers.FOODSAVER.id())).isTrue();

        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(delete("/api/users/me").with(csrf()).cookie(foodsaver).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ich ziehe weg\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").cookie(foodsaver)).andExpect(status().isUnauthorized());

        Map<String, Object> row = jdbc.queryForMap("select email, display_name, active, mobile_phone, avatar_url, "
                + "deleted_at, delete_reason, role from user_account where id = ?", SeedUsers.FOODSAVER.id());
        assertThat(row.get("EMAIL")).isEqualTo("deleted-" + id + "@deleted.invalid");
        assertThat(row.get("DISPLAY_NAME")).isEqualTo("Gelöschtes Mitglied");
        assertThat(row.get("ACTIVE")).isEqualTo(false);
        assertThat(row.get("MOBILE_PHONE")).isNull();
        assertThat(row.get("AVATAR_URL")).isNull();
        assertThat(row.get("DELETED_AT")).isNotNull();
        assertThat(row.get("DELETE_REASON")).isEqualTo("Ich ziehe weg");
        assertThat(row.get("ROLE")).isEqualTo("FOODSHARER");
        assertThat(jdbc.queryForObject("select count(*) from region_membership where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from store_membership where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from auth_identity where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from calendar_token where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        for (String table : List.of("forum_follow", "forum_thread_follow", "forum_post_reaction", "poll_vote", "quiz_session",
                "learning_progress", "user_onboarding_step_done", "passport_issue", "guide_profile")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class,
                    SeedUsers.FOODSAVER.id())).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select author_id from forum_post where id = '00000043-0002-4000-8000-000000000002'",
                String.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("select snapshot_json from user_archive where user_id = ?", String.class,
                SeedUsers.FOODSAVER.id())).contains("foodsaver@local");
        assertThat(jdbc.queryForObject("select count(*) from history_entry where subject_id = ? and action = "
                + "'ACCOUNT_DELETED' and reason = 'Ich ziehe weg'", Integer.class, SeedUsers.FOODSAVER.id()))
                .isEqualTo(1);
        mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + id + "\"}")).andExpect(status().is4xxClientError());
    }

    @Test
    void businessCardForOwnRegionAndRole() throws Exception {
        String region = jdbc.queryForObject("select home_region_id from user_account where id = ?", String.class,
                SeedUsers.BIEB.id());
        Cookie bieb = login(SeedUsers.BIEB);
        byte[] pdf = mvc.perform(get("/api/users/me/business-card").param("role", "STORE_MANAGER")
                        .param("regionId", region).cookie(bieb))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mvc.perform(get("/api/users/me/business-card").param("role", "AMBASSADOR").param("regionId", region)
                .cookie(bieb)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/business-card").param("role", "FOODSAVER")
                .param("regionId", java.util.UUID.randomUUID().toString()).cookie(bieb))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOrgaDeletesOthers() throws Exception {
        String url = "/api/admin/users/" + SeedUsers.SLEEPY.id();
        mvc.perform(delete(url).with(csrf()).cookie(login(SeedUsers.WELCOME))).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(csrf()).cookie(login(SeedUsers.ORGA))).andExpect(status().isNoContent());
        mvc.perform(delete(url).with(csrf()).cookie(login(SeedUsers.ORGA))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select deleted_by from user_archive where user_id = ?", String.class,
                SeedUsers.SLEEPY.id())).isEqualTo(SeedUsers.ORGA.id().toString());
    }
}
