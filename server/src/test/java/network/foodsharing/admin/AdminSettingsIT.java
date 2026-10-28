package network.foodsharing.admin;

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
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** C10, C11, C12, C14: ORGA-only settings, public toggles, BASKETS toggle enforcement, blocklist/IP blocks, uploads. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-settings-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class AdminSettingsIT {

    @Autowired
    private MockMvc mvc;

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

    @Test
    void togglesArePublicChangedByOrgaAndEnforced() throws Exception {
        mvc.perform(get("/api/public/feature-toggles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.BASKETS").value(true)).andExpect(jsonPath("$.DONATION_MODAL").value(false))
                .andExpect(jsonPath("$.PETITION_BANNER").value(true));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/feature-toggles").cookie(foodsaver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/baskets/mine").cookie(foodsaver)).andExpect(status().isOk());
        mvc.perform(json(patch("/api/admin/feature-toggles/BASKETS"), orga, "{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        try {
            mvc.perform(get("/api/baskets/mine").cookie(foodsaver)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mvc.perform(get("/api/public/feature-toggles")).andExpect(jsonPath("$.BASKETS").value(false));
        } finally {
            mvc.perform(json(patch("/api/admin/feature-toggles/BASKETS"), orga, "{\"enabled\":true}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(json(patch("/api/admin/feature-toggles/NOPE"), orga, "{\"enabled\":true}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEATURE_TOGGLE_NOT_FOUND"));
        mvc.perform(get("/api/admin/feature-toggles").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void configurationAndDonations() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/configuration").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'support.email')].value").value("support@local.test"));
        mvc.perform(json(put("/api/admin/configuration/support.email"), orga, "{\"value\":\"nope\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONFIGURATION_VALUE_INVALID"));
        mvc.perform(json(put("/api/admin/configuration/support.email"), orga, "{\"value\":\"help@local.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("help@local.test"));
        mvc.perform(json(put("/api/admin/configuration/support.email"), login(SeedUsers.BOT),
                "{\"value\":\"x@local.test\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/donations").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.showCampaignCard").value(true))
                .andExpect(jsonPath("$.campaignPageSlugs[0]").value("donation-campaign-1"));
        mvc.perform(json(put("/api/admin/donations"), orga, "{\"showCampaignCard\":false,\"showModal\":true,"
                        + "\"modalHoursLoggedIn\":12,\"modalHoursLoggedOut\":6,\"infoUrl\":\"https://x.local.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.showModal").value(true))
                .andExpect(jsonPath("$.modalHoursLoggedIn").value(12));
    }

    @Test
    void blocklistAndIpBlocks() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/email-blocklist").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(json(post("/api/admin/email-blocklist"), orga, "{\"pattern\":\"Mailinator.com\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BLOCKLIST_DUPLICATE"));
        String created = mvc.perform(json(post("/api/admin/email-blocklist"), orga,
                        "{\"pattern\":\"@Trash.Test\",\"reason\":\"Wegwerf\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pattern").value("trash.test"))
                .andExpect(jsonPath("$.createdBy.id").value(SeedUsers.ORGA.id().toString()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(json(patch("/api/admin/email-blocklist/" + id), orga, "{\"pattern\":\"trash2.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pattern").value("trash2.test"));
        mvc.perform(delete("/api/admin/email-blocklist/" + id).with(csrf()).cookie(orga))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/ip-blocks").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].context").value("LOGIN"));
        String block = mvc.perform(json(post("/api/admin/ip-blocks"), orga,
                        "{\"ip\":\"192.0.2.0/24\",\"context\":\"REGISTRATION\",\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(json(post("/api/admin/ip-blocks"), orga, "{\"ip\":\"no-ip\",\"context\":\"ALL\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/ip-blocks/" + JsonPath.read(block, "$.id")).with(csrf()).cookie(orga))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/ip-blocks").cookie(login(SeedUsers.BOT))).andExpect(status().isForbidden());
    }

    @Test
    void uploadAdministration() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/uploads/summary").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
        String page = mvc.perform(get("/api/admin/uploads").param("module", "USER_PHOTO").cookie(orga))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String uploadId = JsonPath.read(page, "$.items[0].id");
        mvc.perform(delete("/api/admin/uploads/" + uploadId).with(csrf()).cookie(login(SeedUsers.BOT)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/uploads/" + uploadId).with(csrf()).cookie(orga))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/uploads/" + uploadId).with(csrf()).cookie(orga))
                .andExpect(status().isNotFound());
    }
}
