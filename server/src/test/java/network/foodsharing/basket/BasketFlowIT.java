package network.foodsharing.basket;

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
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** B1/B2: create → nearby → request (bell + chat message) → picked up; seed baskets; photo is public. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:baskets-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class BasketFlowIT {

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

    @Test
    void seedBasketsAreNearZurich() throws Exception {
        Cookie newbie = login(SeedUsers.NEWBIE);
        String body = mvc.perform(get("/api/baskets/nearby").param("lat", "47.3769").param("lon", "8.5417")
                        .param("distanceKm", "5").cookie(newbie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Double> distances = JsonPath.read(body, "$[*].distanceKm");
        assertThat(distances).hasSize(8).isSorted();
        mvc.perform(get("/api/baskets/requested").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        String photo = jdbc.queryForObject("select photo_upload_id from basket where id = ?", String.class,
                "00000048-0001-4000-8000-000000000001");
        mvc.perform(get("/api/uploads/" + photo)).andExpect(status().isOk());
    }

    @Test
    void createRequestAndPickUp() throws Exception {
        Cookie owner = login(SeedUsers.FOODSAVER);
        String created = mvc.perform(post("/api/baskets").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Frisches Brot\",\"locationType\":\"HOME\",\"contactMessage\":true,"
                                + "\"contactPhone\":false,\"contactMobile\":false,\"kinds\":[\"BAKED_GOODS\"],"
                                + "\"groceryIds\":[\"00000020-0005-4000-8000-000000000001\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.permissions.mayEdit").value(true))
                .andExpect(jsonPath("$.groceries.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        Cookie requester = login(SeedUsers.JUMPER);
        mvc.perform(get("/api/baskets/" + id).cookie(requester))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.mayRequest").value(true))
                .andExpect(jsonPath("$.lat").value(47.389));
        mvc.perform(post("/api/baskets/" + id + "/requests").with(csrf()).cookie(requester)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Darf ich es holen?\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED"));
        mvc.perform(post("/api/baskets/" + id + "/requests").with(csrf()).cookie(requester)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'BASKET_REQUEST'",
                Integer.class, SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from message where sender_id = ? and body = ?",
                Integer.class, SeedUsers.JUMPER.id(), "Darf ich es holen?")).isEqualTo(1);

        mvc.perform(patch("/api/baskets/" + id + "/requests/" + SeedUsers.JUMPER.id()).with(csrf()).cookie(requester)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/baskets/" + id).cookie(owner))
                .andExpect(jsonPath("$.requests.length()").value(1));
        mvc.perform(patch("/api/baskets/" + id + "/requests/" + SeedUsers.JUMPER.id()).with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PICKED_UP"));
        mvc.perform(get("/api/baskets/" + id).cookie(requester))
                .andExpect(jsonPath("$.status").value("TAKEN"))
                .andExpect(jsonPath("$.myRequest.status").value("PICKED_UP"));
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = "
                + "'BASKET_REQUEST_ANSWERED'", Integer.class, SeedUsers.JUMPER.id())).isEqualTo(1);

        mvc.perform(delete("/api/baskets/" + id).with(csrf()).cookie(requester)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/baskets/" + id).with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/baskets/" + id).cookie(owner)).andExpect(status().isNotFound());
    }
}
