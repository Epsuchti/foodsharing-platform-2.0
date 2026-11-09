package network.foodsharing.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Decision 173: staging without a panel password has no persona beans; the endpoints answer 404. */
@SpringBootTest(properties = "app.staging.persona-panel.password=")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "staging"})
class PersonaPanelWithoutPasswordTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void noPersonaBeansAndTheEndpointsAre404() throws Exception {
        assertThat(Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(type -> type != null && type.getName().startsWith("network.foodsharing.persona.")))
                .isEmpty();
        mvc.perform(post("/api/personas/list").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/personas/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"\",\"userId\":\"" + SeedUsers.ORGA.id() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void appInfoHasTheBannerButNoPanel() throws Exception {
        mvc.perform(get("/api/public/app-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPanel").value(false))
                .andExpect(jsonPath("$.demoBanner").value(true));
    }
}
