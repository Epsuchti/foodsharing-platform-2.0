package network.foodsharing.fsp;

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
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FS1: seed FSPs, member proposal → activation bell → activation, follow flags → bell/mail, wall, managers. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fsps-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class FspFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String WIEDIKON = "00000049-0001-4000-8000-000000000001";
    private static final String ALTSTETTEN = "00000049-0001-4000-8000-000000000005";

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

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void seedListsAndDetails() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        String list = mvc.perform(get("/api/regions/" + ZUERICH + "/food-share-points").cookie(jumper))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(list, "$[*].name");
        assertThat(names).hasSize(4).contains("Fairteiler Wiedikon").isSorted();
        mvc.perform(get("/api/regions/" + ZUERICH + "/food-share-points").param("includeInactive", "true")
                .cookie(login(SeedUsers.BOT))).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/food-share-points/" + ALTSTETTEN).cookie(jumper)).andExpect(status().isNotFound());
        mvc.perform(get("/api/food-share-points/" + WIEDIKON).cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.managers[0].id").value(SeedUsers.BIEB.id().toString()))
                .andExpect(jsonPath("$.following.following").value(true))
                .andExpect(jsonPath("$.permissions.mayEdit").value(false))
                .andExpect(jsonPath("$.permissions.mayPost").value(true));
        mvc.perform(get("/api/food-share-points/" + WIEDIKON + "/wall").cookie(jumper))
                .andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get("/api/food-share-points/mine").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(jsonPath("$.length()").value(3));
        String photo = jdbc.queryForObject("select photo_upload_id from food_share_point where id = ?", String.class,
                WIEDIKON);
        mvc.perform(get("/api/uploads/" + photo)).andExpect(status().isOk());
    }

    @Test
    void memberProposesActivatorActivatesFollowersAreNotified() throws Exception {
        Cookie member = login(SeedUsers.GUIDE);
        String created = mvc.perform(post("/api/regions/" + ZUERICH + "/food-share-points").with(csrf())
                        .cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fairteiler Seebach\",\"street\":\"Seebacherstrasse 1\","
                                + "\"postcode\":\"8052\",\"city\":\"Zürich\",\"lat\":47.42,\"lon\":8.54}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.managers[0].id").value(SeedUsers.GUIDE.id().toString()))
                .andExpect(jsonPath("$.permissions.mayActivate").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        assertThat(count("select count(*) from bell where user_id = ? and type = 'FSP_ACTIVATE'",
                SeedUsers.BOT.id())).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where subject_id = ?", java.util.UUID.fromString(id)))
                .isGreaterThanOrEqualTo(2);

        mvc.perform(put("/api/food-share-points/" + id + "/status").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(put("/api/food-share-points/" + id + "/status").with(csrf()).cookie(bot)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(count("select count(*) from bell where type = 'FSP_ACTIVATE' and identifier = ? "
                + "and deleted_at is null",
                "FSP_ACTIVATE-" + id)).isZero();

        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(put("/api/food-share-points/" + id + "/follow").with(csrf()).cookie(foodsaver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bell\":true,\"mail\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mail").value(true));
        mvc.perform(patch("/api/food-share-points/" + id).with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Neues Regal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Neues Regal"));
        String post = mvc.perform(post("/api/food-share-points/" + id + "/wall").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Regal ist voll!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(count("select count(*) from bell where user_id = ? and type = 'FSP_NEW_POST'",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'FSP_NEW_POST'",
                SeedUsers.GUIDE.id())).isZero();
        assertThat(count("select count(*) from mail_outbox where template = 'notification/FSP_NEW_POST'"))
                .isEqualTo(1);

        String postId = JsonPath.read(post, "$.id");
        mvc.perform(delete("/api/food-share-points/" + id + "/wall/" + postId).with(csrf()).cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/food-share-points/" + id + "/wall/" + postId).with(csrf()).cookie(bot))
                .andExpect(status().isNoContent());

        mvc.perform(put("/api/food-share-points/" + id + "/managers").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + SeedUsers.NEWBIE.id() + "\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/food-share-points/" + id + "/managers").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + SeedUsers.BIEB.id() + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.managers.length()").value(1))
                .andExpect(jsonPath("$.managers[0].id").value(SeedUsers.BIEB.id().toString()));

        mvc.perform(delete("/api/food-share-points/" + id + "/follow").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/food-share-points/" + id).with(csrf()).cookie(member))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/food-share-points/" + id).with(csrf()).cookie(bot))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/food-share-points/" + id).cookie(bot)).andExpect(status().isNotFound());
    }

    @Test
    void outsidersMayNotCreate() throws Exception {
        mvc.perform(post("/api/regions/" + ZUERICH + "/food-share-points").with(csrf())
                        .cookie(login(SeedUsers.NEWBIE)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"street\":\"Y 1\",\"postcode\":\"8000\",\"city\":\"Zürich\"}"))
                .andExpect(status().isForbidden());
    }
}
