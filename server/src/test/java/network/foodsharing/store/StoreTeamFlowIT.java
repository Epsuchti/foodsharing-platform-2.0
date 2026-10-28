package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
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

/** Team workflow on the seed (e2e profile): apply, accept, standby, regular, remove, invitations, manager limits. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-team-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class StoreTeamFlowIT {

    static final String S = "/api/stores/00000020-0001-4000-8000-0000000000";

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

    private int bells(SeedUsers user, String type) {
        return jdbc.queryForObject("select count(*) from bell where user_id = ? and type = ? and deleted_at is null", Integer.class,
                user.id(), type);
    }

    @Test
    void applyAcceptStandbyRegularAndRemove() throws Exception {
        mvc.perform(json(post(S + "05/applications").cookie(login(SeedUsers.QUIZ)), "{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_TOO_LOW"));
        mvc.perform(json(post(S + "04/applications").cookie(login(SeedUsers.GUIDE)), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_CLOSED"));
        mvc.perform(json(post(S + "01/applications").cookie(login(SeedUsers.FOODSAVER)), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
        Cookie sleepy = login(SeedUsers.SLEEPY);
        mvc.perform(json(post(S + "05/applications").cookie(sleepy), "{\"text\":\"Gerne!\"}"))
                .andExpect(status().isNoContent());
        assertThat(bells(SeedUsers.BIEB, "STORE_NEW_REQUEST")).isEqualTo(1);
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get(S + "05/team").cookie(bieb))
                .andExpect(jsonPath("$.applications[0].text").value("Gerne!"));
        String user = "/" + SeedUsers.SLEEPY.id();
        mvc.perform(post(S + "05/applications" + user + "/accept").param("moveToStandby", "true").cookie(bieb).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(S + "05/permissions").cookie(sleepy)).andExpect(jsonPath("$.teamStatus").value("WAITING_LIST"));
        mvc.perform(post(S + "05/team" + user + "/regular").cookie(bieb).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(json(post(S + "05/team" + user + "/standby").cookie(bieb), "{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post(S + "05/team" + user + "/standby").cookie(bieb), "{\"message\":\"Pause bis Ende Monat\"}"))
                .andExpect(status().isNoContent());
        assertThat(bells(SeedUsers.SLEEPY, "STORE_MOVED_TO_STANDBY")).isEqualTo(1);
        mvc.perform(post(S + "05/team" + user + "/regular").cookie(bieb).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put(S + "05/managers" + user).cookie(bieb).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_TOO_LOW"));
        mvc.perform(json(delete(S + "05/team" + user).cookie(login(SeedUsers.FOODSAVER)), "{\"message\":\"Bitte entfernen danke\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(delete(S + "05/team" + user).cookie(bieb), "{\"message\":\"Mehrmals nicht erschienen\"}"))
                .andExpect(status().isNoContent());
        assertThat(bells(SeedUsers.SLEEPY, "STORE_MEMBER_REMOVED")).isEqualTo(1);
        assertThat(jdbc.queryForList("select action from history_entry where secondary_subject_id = ? and "
                        + "subject_type = 'STORE' order by occurred_at", String.class, SeedUsers.SLEEPY.id()))
                .contains("REQUEST_TO_JOIN", "REQUEST_APPROVED", "MOVED_TO_JUMPER", "MOVED_TO_TEAM",
                        "REMOVED_FROM_STORE");
        // A23: sleepy switched mails off in every category (seed 101), so the removal is a bell without mail.
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox where template = 'store/member-removed'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'STORE_MEMBER_REMOVED' "
                + "and mail_outbox_id is null", Integer.class, SeedUsers.SLEEPY.id())).isEqualTo(1);
    }

    @Test
    void managerLimitsAndLeaving() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(post(S + "11/team/" + SeedUsers.REPORTS.id()).cookie(bot).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put(S + "11/managers/" + SeedUsers.REPORTS.id()).cookie(bot).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOO_MANY_MANAGERS"));
        mvc.perform(json(delete(S + "03/managers/" + SeedUsers.BOT.id()).cookie(bot), "{\"message\":\"Pause bis Ende Monat\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_MANAGER"));
        mvc.perform(delete(S + "03/team/" + SeedUsers.BOT.id()).cookie(bot).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANAGER_CANNOT_LEAVE"));
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(delete(S + "03/team/" + SeedUsers.GUIDE.id()).cookie(guide).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'LEFT_STORE' "
                + "and secondary_subject_id = ?", Integer.class, SeedUsers.GUIDE.id())).isEqualTo(1);
    }

    @Test
    void invitationsAndApplicationCancel() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        mvc.perform(post(S + "08/invitations/me/accept").cookie(jumper).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(S + "08/permissions").cookie(jumper)).andExpect(jsonPath("$.teamStatus").value("MEMBER"));
        mvc.perform(post(S + "08/invitations/me/decline").cookie(jumper).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_INVITED"));
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(post(S + "01/invitations/" + SeedUsers.QUIZ.id()).cookie(bieb).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_TOO_LOW"));
        mvc.perform(post(S + "01/invitations/" + SeedUsers.GUIDE.id()).cookie(bieb).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(bells(SeedUsers.GUIDE, "STORE_INVITATION")).isEqualTo(1);
        mvc.perform(delete(S + "01/invitations/" + SeedUsers.GUIDE.id()).cookie(bieb).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(bells(SeedUsers.GUIDE, "STORE_INVITATION")).isZero();
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(delete(S + "07/applications/me").cookie(guide).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(S + "07/applications/me").cookie(guide).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_APPLIED"));
    }

    @Test
    void teamListShowsPhonesToMembersButNoApplications() throws Exception {
        mvc.perform(get(S + "01/team").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].isManager").value(true))
                .andExpect(jsonPath("$.members[0].user.phone").isNotEmpty())
                .andExpect(jsonPath("$.jumpers[0].user.id").value(SeedUsers.JUMPER.id().toString()))
                .andExpect(jsonPath("$.applications").isEmpty());
        mvc.perform(get(S + "01/team").cookie(login(SeedUsers.JUMPER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.members[0].user.phone").doesNotExist());
        mvc.perform(get(S + "01/team").cookie(login(SeedUsers.BIEB)))
                .andExpect(jsonPath("$.applications").isArray()).andExpect(jsonPath("$.members[0].distanceKm").exists());
    }
}
