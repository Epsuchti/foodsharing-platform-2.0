package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Decision 159: SessionInfo.capabilities on the seed (menu items Verwalten / Orga). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-capabilities-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class SessionCapabilitiesIT {

    @Autowired
    private MockMvc mvc;

    private List<String> capabilities(SeedUsers user) throws Exception {
        var login = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        Cookie session = Arrays.stream(login.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
        String body = mvc.perform(get("/api/auth/session").cookie(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return JsonPath.read(body, "$.capabilities");
    }

    @Test
    void workingGroupAdminNinaGetsNoCapability() throws Exception {
        assertThat(capabilities(SeedUsers.BIEB)).isEmpty();
    }

    @Test
    void plainFoodsaverGetsNone() throws Exception {
        assertThat(capabilities(SeedUsers.FOODSAVER)).isEmpty();
    }

    @Test
    void ambassadorOfZurichManagesUsersNewslettersBlogAndContent() throws Exception {
        // Reto is ambassador of Zürich and admin of its PR group (seed region_admin)
        assertThat(capabilities(SeedUsers.BOT)).contains("MANAGE_NEWCOMERS", "VERIFY", "MANAGE_MEMBERS", "NEWSLETTERS",
                "BLOG_MANAGE", "CONTENT_EDIT");
    }

    @Test
    void orgaGetsEveryCapability() throws Exception {
        assertThat(capabilities(SeedUsers.ORGA)).containsExactlyInAnyOrder("QUIZ_EDIT", "MANAGE_NEWCOMERS", "VERIFY",
                "MANAGE_MEMBERS", "NEWSLETTERS", "BLOG_MANAGE", "CONTENT_EDIT");
    }
}
