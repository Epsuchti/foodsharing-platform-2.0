package network.foodsharing.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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

/**
 * M1 user layer matrix (upstream MapPermissions): only administrators of the region (ambassadors, FSMANAGEMENT
 * admins, ORGA) see member markers and user bubbles; positions are rounded to about 100 m; never anonymous.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:usermarkers-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class UserMarkersPermissionTest {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String USERS = "/api/map/markers/users";

    @Autowired
    private MockMvc mvc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void anonymousNeverSeesUsers() throws Exception {
        mvc.perform(get(USERS).param("regionId", ZUERICH)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/map/bubbles/user/" + SeedUsers.JUMPER.id())).andExpect(status().isNotFound());
    }

    @Test
    void plainMembersAreForbidden() throws Exception {
        for (SeedUsers user : List.of(SeedUsers.FOODSAVER, SeedUsers.JUMPER, SeedUsers.NEWBIE)) {
            Cookie cookie = login(user);
            mvc.perform(get(USERS).param("regionId", ZUERICH).cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/api/map/bubbles/user/" + SeedUsers.SLEEPY.id()).cookie(cookie))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void regionAdministratorsAndOrgaSeeRoundedPositions() throws Exception {
        for (SeedUsers user : List.of(SeedUsers.BOT, SeedUsers.ORGA)) {
            Cookie cookie = login(user);
            String body = mvc.perform(get(USERS).param("regionId", ZUERICH).cookie(cookie))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> lats = JsonPath.read(body, "$[*].lat");
            List<Number> lons = JsonPath.read(body, "$[*].lon");
            assertThat(lats).isNotEmpty();
            assertThat(lats).allMatch(UserMarkersPermissionTest::atMostThreeDecimals);
            assertThat(lons).allMatch(UserMarkersPermissionTest::atMostThreeDecimals);
            mvc.perform(get("/api/map/bubbles/user/" + SeedUsers.JUMPER.id()).cookie(cookie))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unknownRegionIsNotFound() throws Exception {
        mvc.perform(get(USERS).param("regionId", "00000010-0001-4000-8000-0000000009ff").cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isNotFound());
    }

    private static boolean atMostThreeDecimals(Number value) {
        return new BigDecimal(value.toString()).stripTrailingZeros().scale() <= 3;
    }
}
