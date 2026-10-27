package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * The region, group, region-admin and public-region APIs end to end on the local seed (H2, real security, real
 * transactions so the after-commit notifications run).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:region-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false", "app.region.group-mails-per-hour=50"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RegionApiIT {

    static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    static final String WINTERTHUR = "00000010-0001-4000-8000-000000000041";
    static final String CANTON = "00000010-0001-4000-8000-000000000010";
    static final String PR_GROUP = "00000011-0001-4000-8000-000000000028";
    static final String KOCHGRUPPE = "00000011-0001-4000-8000-000000000033";

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

    @Test
    void regionPageMembersStatisticsAndSession() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZUERICH)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/regions/" + ZUERICH).cookie(foodsaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("zuerich-stadt"))
                .andExpect(jsonPath("$.ambassadors[0].displayName").value("Reto Baumann"))
                .andExpect(jsonPath("$.welcomeTeam.slug").value("begruessungsteam-zuerich"))
                .andExpect(jsonPath("$.functions.length()").value(13))
                .andExpect(jsonPath("$.membership.homeRegion").value(true))
                .andExpect(jsonPath("$.statistics.memberCount").value(12))
                .andExpect(jsonPath("$.statistics.genderCounts.FEMALE").value(6));
        mvc.perform(get("/api/regions/" + ZUERICH + "/members").param("verified", "false").cookie(foodsaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalItems").value(3));
        mvc.perform(get("/api/regions/" + ZUERICH + "/permissions").cookie(foodsaver))
                .andExpect(status().isOk());
        mvc.perform(get("/api/regions/" + ZUERICH + "/children").param("includeGroups", "true").cookie(foodsaver))
                .andExpect(jsonPath("$.length()").value(16));
        mvc.perform(get("/api/regions/search").param("q", "zürich").param("types", "CITY").cookie(foodsaver))
                .andExpect(jsonPath("$.items[0].slug").value("zuerich-stadt"));
        mvc.perform(get("/api/regions/" + WINTERTHUR + "/members").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/regions/" + ZUERICH + "/settings").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/regions/" + ZUERICH + "/history").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/regions/" + ZUERICH + "/pin").cookie(foodsaver))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/regions/" + ZUERICH + "/conference").cookie(foodsaver))
                .andExpect(jsonPath("$.url").value("https://meet.jit.si/foodsharing-zuerich-stadt"));
        mvc.perform(get("/api/regions/" + "00000000-0000-4000-8000-000000000000").cookie(foodsaver))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REGION_NOT_FOUND"));

        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(get("/api/auth/session").cookie(guide))
                .andExpect(jsonPath("$.units[?(@.slug == 'zuerich-stadt')].homeRegion").value(true))
                .andExpect(jsonPath("$.units[?(@.slug == 'begruessungsteam-zuerich')].function").value("WELCOME"))
                .andExpect(jsonPath("$.permissions[0]").value("CAN_GIVE_INTRO_PICKUPS@" + ZUERICH))
                .andExpect(jsonPath("$.user.homeRegionId").value(ZUERICH));
    }

    @Test
    void optionsFollowTheFunctionGroups() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZUERICH + "/options/permissions").cookie(bot))
                .andExpect(jsonPath("$.mayEditPickupRule").value(false))
                .andExpect(jsonPath("$.mayEditUserRelated").value(true))
                .andExpect(jsonPath("$.mayEditPin").value(true));
        mvc.perform(json(patch("/api/regions/" + ZUERICH + "/options").cookie(bot),
                        "{\"pickupRule\":{\"active\":false,\"timespanDays\":1,\"limitNumber\":1,\"limitDayNumber\":1,"
                                + "\"inactiveHours\":1}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPTION_NOT_EDITABLE"))
                .andExpect(jsonPath("$.args.option").value("pickupRule"));
        mvc.perform(json(patch("/api/regions/" + ZUERICH + "/options").cookie(bot), "{\"allowHidingInForum\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowHidingInForum").value(true))
                .andExpect(jsonPath("$.pickupRule.timespanDays").value(7));
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(patch("/api/regions/" + ZUERICH + "/options").cookie(bieb),
                        "{\"pickupRule\":{\"active\":true,\"timespanDays\":14,\"limitNumber\":2,\"limitDayNumber\":1,"
                                + "\"inactiveHours\":24}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pickupRule.timespanDays").value(14));
        mvc.perform(get("/api/regions/" + ZUERICH + "/history").param("actions", "OPTION_CHANGED").cookie(bot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("OPTION_CHANGED"));
        mvc.perform(get("/api/regions/" + ZUERICH + "/settings").cookie(bot))
                .andExpect(jsonPath("$.introPickups.ignoredCategories[0]").value("ONLINE"))
                .andExpect(jsonPath("$.onboarding.extraSteps[0].titles.fr")
                        .value("Participer à une rencontre des nouveaux"));
    }

    @Test
    void groupApplicationDecisionNotifiesTheApplicant() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(post("/api/groups/" + PR_GROUP + "/applications").cookie(foodsaver), "{\"text\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
        Cookie jumper = login(SeedUsers.JUMPER);
        mvc.perform(get("/api/groups/" + PR_GROUP).cookie(jumper))
                .andExpect(jsonPath("$.mayApply").value(true))
                .andExpect(jsonPath("$.admins[0].displayName").value("Reto Baumann"));
        String body = mvc.perform(json(post("/api/groups/" + PR_GROUP + "/applications").cookie(jumper),
                        "{\"text\":\"Zweiter Versuch\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String applicationId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        mvc.perform(get("/api/dev/notifications").param("type", "GROUP_APPLICATION_RECEIVED")
                        .param("userId", SeedUsers.BOT.id().toString()))
                .andExpect(jsonPath("$[0].params.userName").value("Jonas Weber"));

        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/groups/" + PR_GROUP + "/applications").cookie(bot))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(json(post("/api/groups/" + PR_GROUP + "/applications/" + applicationId + "/decision").cookie(bot),
                        "{\"accept\":true,\"message\":\"Willkommen!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(get("/api/dev/notifications").param("type", "GROUP_APPLICATION_ACCEPTED")
                        .param("userId", SeedUsers.JUMPER.id().toString()))
                .andExpect(jsonPath("$[?(@.channel == 'MAIL')]").isNotEmpty());
        mvc.perform(get("/api/groups/" + PR_GROUP + "/members").cookie(jumper))
                .andExpect(jsonPath("$[?(@.user.displayName == 'Jonas Weber')]").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox where template = ?", Integer.class,
                "region/group-application-accepted")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select subject from mail_outbox where template = ?", String.class,
                "region/group-application-accepted")).contains("Öffentlichkeitsarbeit Zürich");

        mvc.perform(json(put("/api/groups/" + KOCHGRUPPE + "/members/me").cookie(bot), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.membership.member").value(true));
        mvc.perform(json(post("/api/groups/" + KOCHGRUPPE + "/mail").cookie(bot),
                        "{\"subject\":\"Kochabend\",\"body\":\"Am Freitag um 19 Uhr\"}"))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox where template = 'region/group-mail'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void regionAdministrationAndPublicEndpoints() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/admin/regions").cookie(bot)).andExpect(status().isForbidden());
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/regions").param("parentId", CANTON).cookie(orga))
                .andExpect(jsonPath("$.items[0].region.slug").value("zuerich-stadt"))
                .andExpect(jsonPath("$.items[0].memberCount").value(12));
        mvc.perform(json(post("/api/admin/regions").cookie(orga), "{\"name\":\"Kreis 4\",\"type\":\"PART_OF_TOWN\","
                        + "\"parentId\":\"" + WINTERTHUR + "\",\"timeZone\":\"Europe/Zurich\",\"joinMode\":\"OPEN\","
                        + "\"moderated\":false,\"emailName\":\"zuerich\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_NAME_TAKEN"));
        mvc.perform(json(post("/api/admin/regions").cookie(orga), "{\"name\":\"Seen\",\"type\":\"PART_OF_TOWN\","
                        + "\"parentId\":\"" + WINTERTHUR + "\",\"timeZone\":\"Europe/Zurich\",\"joinMode\":\"OPEN\","
                        + "\"moderated\":false}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.region.slug").value("seen"));
        mvc.perform(json(post("/api/admin/regions/" + ZUERICH + "/move").cookie(orga),
                        "{\"newParentId\":\"" + ZUERICH + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_MOVE_INTO_DESCENDANT"));
        mvc.perform(json(delete("/api/admin/regions/" + ZUERICH).cookie(orga), "{\"reason\":\"no\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGION_HAS_CHILDREN"));
        mvc.perform(json(post("/api/admin/regions/import").cookie(orga),
                        "{\"csv\":\"winterthur,toess,Töss,PART_OF_TOWN,,CH,8406\",\"dryRun\":true}"))
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.rows[0].status").value("CREATE"));

        mvc.perform(get("/api/public/regions/zuerich-stadt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("zuerich@localhost"))
                .andExpect(jsonPath("$.ambassadorNames[0]").value("Reto Baumann"))
                .andExpect(jsonPath("$.pin.active").value(true))
                .andExpect(jsonPath("$.nextIntroPickups").isArray());
        mvc.perform(get("/api/public/regions/begruessungsteam-zuerich")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/regions/tree").param("rootSlug", "kanton-zuerich"))
                .andExpect(jsonPath("$[0].children[?(@.slug == 'zuerich-stadt')].active").value(true))
                .andExpect(jsonPath("$[0].children[?(@.slug == 'winterthur')].active").value(false));
        mvc.perform(get("/api/public/regions/suggest").param("postcode", "8004"))
                .andExpect(jsonPath("$[0].region.slug").value("zuerich-stadt"))
                .andExpect(jsonPath("$[0].matchedBy").value("POSTCODE"));
        mvc.perform(get("/api/public/regions/suggest").param("postcode", "8500"))
                .andExpect(jsonPath("$[0].region.slug").value("zuerich-stadt"))
                .andExpect(jsonPath("$[0].matchedBy").value("DISTANCE"));
        mvc.perform(get("/api/public/regions/suggest")).andExpect(status().isBadRequest());
    }
}
