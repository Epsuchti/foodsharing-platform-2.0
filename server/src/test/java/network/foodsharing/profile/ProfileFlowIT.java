package network.foodsharing.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** A6, A9, A15, A16, G4 on the seed (fixed clock 2026-11-02T09:00Z): profile, sleep, bananas, buddies, wall. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profiles-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileFlowIT {

    static final String LONG_MESSAGE = "Danke für die vielen gemeinsamen Abholungen, die gute Laune und die Hilfe "
            + "beim Fairteiler – du bist eine echte Stütze!";

    @Autowired
    private MockMvc mvc;

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
        return mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private List<String> bellTypes(Cookie user) throws Exception {
        return JsonPath.read(body(get("/api/bells?size=100").cookie(user)), "$.items[*].type");
    }

    @Test
    @Order(1)
    void profileShowsSeedStatsBuddiesAndPermissions() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/profile").cookie(bieb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Sarah Müller"))
                .andExpect(jsonPath("$.user.sleepMode").value(false))
                .andExpect(jsonPath("$.stats.bananaCount").value(5))
                .andExpect(jsonPath("$.stats.buddyCount").value(2))
                .andExpect(jsonPath("$.banana.givenByMe").value(true))
                .andExpect(jsonPath("$.banana.mayGive").value(false))
                .andExpect(jsonPath("$.buddy.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.permissions.mayEditProfile").value(false))
                .andExpect(jsonPath("$.permissions.maySeeNotes").value(false));

        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/profile").cookie(orga))
                .andExpect(jsonPath("$.permissions.mayAdminister").value(true))
                .andExpect(jsonPath("$.permissions.maySeeCommitments").value(true))
                .andExpect(jsonPath("$.stores").isArray());
        mvc.perform(get("/api/users/" + SeedUsers.SLEEPY.id() + "/profile").cookie(orga))
                .andExpect(jsonPath("$.user.sleepMode").value(true));

        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/profile").cookie(newbie))
                .andExpect(jsonPath("$.banana.mayGive").value(false))
                .andExpect(jsonPath("$.stores").doesNotExist());
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/commitments").cookie(newbie))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/00000001-0001-4000-8000-000000000999/profile").cookie(newbie))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(2)
    void commitmentsForSelf() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String json = body(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/commitments?weeks=8").cookie(foodsaver));
        assertThat((List<Object>) JsonPath.read(json, "$.weeks")).hasSize(8);
        assertThat((String) JsonPath.read(json, "$.weeks[7].weekStart")).isEqualTo("2026-11-02");
    }

    @Test
    @Order(3)
    void bananaGiveListDelete() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        String target = SeedUsers.JUMPER.id().toString();
        mvc.perform(json(post("/api/users/" + target + "/bananas"), "{\"message\":\"" + "zu kurz" + "\"}")
                .cookie(guide)).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/users/" + target + "/bananas"), "{\"message\":\"" + LONG_MESSAGE + "\"}")
                .cookie(guide)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender.id").value(SeedUsers.GUIDE.id().toString()));
        mvc.perform(json(post("/api/users/" + target + "/bananas"), "{\"message\":\"" + LONG_MESSAGE + "\"}")
                .cookie(guide)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANANA_ALREADY_GIVEN"));
        mvc.perform(json(post("/api/users/" + SeedUsers.GUIDE.id() + "/bananas"),
                "{\"message\":\"" + LONG_MESSAGE + "\"}").cookie(guide)).andExpect(status().isForbidden());

        Cookie jumper = login(SeedUsers.JUMPER);
        assertThat(bellTypes(jumper)).contains("BANANA_GIVEN");
        String received = body(get("/api/users/" + target + "/bananas?direction=received").cookie(jumper));
        assertThat((List<Object>) JsonPath.read(received, "$")).hasSize(2);
        String sent = body(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/bananas?direction=sent").cookie(jumper));
        assertThat((List<Object>) JsonPath.read(sent, "$")).hasSize(3);

        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(delete("/api/users/" + target + "/bananas/" + SeedUsers.GUIDE.id()).with(csrf()).cookie(newbie))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + target + "/bananas/" + SeedUsers.GUIDE.id()).with(csrf()).cookie(jumper))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/" + target + "/profile").cookie(guide))
                .andExpect(jsonPath("$.stats.bananaCount").value(1))
                .andExpect(jsonPath("$.banana.mayGive").value(true));
    }

    @Test
    @Order(4)
    void buddyRequestAcceptRemove() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String requests = body(get("/api/users/me/buddies/requests").cookie(foodsaver));
        assertThat((List<String>) JsonPath.read(requests, "$[*].id")).containsExactly(SeedUsers.JUMPER.id().toString());
        mvc.perform(post("/api/users/" + SeedUsers.JUMPER.id() + "/buddies").with(csrf()).cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        String mine = body(get("/api/users/me/buddies").cookie(foodsaver));
        assertThat((List<Object>) JsonPath.read(mine, "$")).hasSize(3);

        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(post("/api/users/" + SeedUsers.ORGA.id() + "/buddies").with(csrf()).cookie(guide))
                .andExpect(jsonPath("$.status").value("REQUESTED_BY_ME"));
        mvc.perform(post("/api/users/" + SeedUsers.GUIDE.id() + "/buddies").with(csrf()).cookie(guide))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUDDY_SELF"));
        Cookie orga = login(SeedUsers.ORGA);
        assertThat(bellTypes(orga)).contains("BUDDY_REQUEST");
        mvc.perform(delete("/api/users/" + SeedUsers.GUIDE.id() + "/buddies").with(csrf()).cookie(orga))
                .andExpect(status().isNoContent());
        assertThat(bellTypes(orga)).doesNotContain("BUDDY_REQUEST");
        mvc.perform(get("/api/users/" + SeedUsers.ORGA.id() + "/profile").cookie(guide))
                .andExpect(jsonPath("$.buddy.status").value("NONE"));
    }

    @Test
    @Order(5)
    void profileWallOwnerWritesOthersRead() throws Exception {
        String wall = "/api/users/" + SeedUsers.FOODSAVER.id() + "/wall";
        Cookie bieb = login(SeedUsers.BIEB);
        String page = body(get(wall).cookie(bieb));
        assertThat((List<Object>) JsonPath.read(page, "$.items")).hasSize(6);
        mvc.perform(json(post(wall), "{\"body\":\"Hallo\"}").cookie(bieb)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WALL_NOT_ACCESSIBLE"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String created = body(json(post(wall), "{\"body\":\"Neuer Status\"}").cookie(foodsaver));
        String id = JsonPath.read(created, "$.id");
        assertThat((Boolean) JsonPath.read(created, "$.mayDelete")).isTrue();
        mvc.perform(delete(wall + "/" + id).with(csrf()).cookie(bieb)).andExpect(status().isForbidden());
        mvc.perform(delete(wall + "/" + id).with(csrf()).cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(6)
    void sleepModeIsValidatedAndShownOnTheSummary() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(json(put("/api/users/me/sleep-mode"),
                "{\"status\":\"TEMPORARY\",\"from\":\"2026-11-10\",\"until\":\"2026-11-01\"}").cookie(guide))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SLEEP_DATES_INVALID"));
        mvc.perform(json(put("/api/users/me/sleep-mode"),
                "{\"status\":\"TEMPORARY\",\"from\":\"2026-11-01\",\"until\":\"2026-11-20\",\"message\":\"Ferien\"}")
                .cookie(guide)).andExpect(status().isOk()).andExpect(jsonPath("$.sleepMode").value(true))
                .andExpect(jsonPath("$.sleepMessage").value("Ferien"));
        mvc.perform(get("/api/users/me").cookie(guide)).andExpect(jsonPath("$.sleepStatus").value("TEMPORARY"))
                .andExpect(jsonPath("$.sleepUntil").value("2026-11-20"));
        mvc.perform(json(put("/api/users/me/sleep-mode"), "{\"status\":\"NONE\"}").cookie(guide))
                .andExpect(jsonPath("$.sleepMode").value(false));
    }
}
