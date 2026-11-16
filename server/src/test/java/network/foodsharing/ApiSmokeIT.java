package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.notification.newsletter.service.NewsletterTokenService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Decision 180: one smoke per {@code @RestController} on the seed, in one shared context. Per controller: an
 * anonymous call gets 401 (or the documented public answer), one call as a role that may use it is 2xx, and, where
 * the controller has a protected endpoint, one call as a role that may not gets 403 (or the documented refusal). The
 * rules behind each endpoint are unit-tested in the modules; this proves the wiring: security chain, the generated
 * interface, the controller, the service and the seed.
 *
 * <p>{@link #everyRestControllerHasASmokeRow()} scans the code base: a new controller without a row fails it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-smoke-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false",
        "app.telegram.webhook-secret=smoke-telegram-secret",
        "app.mailbox.webhook-secret=smoke-mailbox-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiSmokeIT {

    private static final String R = "00000010-0001-4000-8000-000000000040"; // zuerich-stadt
    private static final String BERLIN = "00000010-0001-4000-8000-000000000050";
    private static final String S01 = "00000020-0001-4000-8000-000000000001"; // bieb manages, foodsaver member
    private static final String FSP01 = "00000049-0001-4000-8000-000000000001"; // Wiedikon, manager bieb
    private static final String REPORT_GROUP = "00000011-0001-4000-8000-000000000024";
    private static final String AVATAR = "00000040-0001-4000-8000-000000000106";
    private static final String CALENDAR_TOKEN = "0123456789abcdef".repeat(4);
    private static final String MIME = Base64.getEncoder().encodeToString(("From: someone@example.org\r\n"
            + "To: zuerich@local.test\r\nSubject: Smoke\r\n\r\nHello\r\n").getBytes(StandardCharsets.UTF_8));
    private static final String CSV = "name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,"
            + "team_status,public_info,manager_foodsaver_numbers\nSmoke Store,,,Bahnhofstrasse 1,8001,Zürich,47.37,8.54,,,,\n";

    /** Who sends a request: {@code null} is anonymous. */
    private record Call(SeedUsers user, Function<ApiSmokeIT, MockHttpServletRequestBuilder> request, int status) {
    }

    /** One row per controller. {@code allowed} is 2xx; {@code denied} is {@code null} without a protected endpoint. */
    private record Row(Class<?> controller, Call anonymous, Call allowed, Call denied) {
        @Override
        public String toString() {
            return controller.getSimpleName();
        }
    }

    private static Function<ApiSmokeIT, MockHttpServletRequestBuilder> get(String path) {
        return test -> MockMvcRequestBuildersGet.of(path);
    }

    private static Function<ApiSmokeIT, MockHttpServletRequestBuilder> postJson(String path, String json) {
        return test -> post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static Call anon(Function<ApiSmokeIT, MockHttpServletRequestBuilder> request, int status) {
        return new Call(null, request, status);
    }

    private static Call as(SeedUsers user, Function<ApiSmokeIT, MockHttpServletRequestBuilder> request, int status) {
        return new Call(user, request, status);
    }

    private static Call as(SeedUsers user, Function<ApiSmokeIT, MockHttpServletRequestBuilder> request) {
        return new Call(user, request, 200);
    }

    /** A protected GET: anonymous 401, {@code ok} gets 200, {@code no} gets 403 ({@code no} may be null). */
    private static Row guarded(Class<?> controller, String path, SeedUsers ok, SeedUsers no) {
        return new Row(controller, anon(get(path), 401), as(ok, get(path)), no == null ? null : as(no, get(path), 403));
    }

    private static Row row(Class<?> controller, Call anonymous, Call allowed, Call denied) {
        return new Row(controller, anonymous, allowed, denied);
    }

    private static final SeedUsers ORGA = SeedUsers.ORGA;
    private static final SeedUsers FS = SeedUsers.FOODSAVER;
    private static final SeedUsers BIEB = SeedUsers.BIEB;
    private static final SeedUsers BOT = SeedUsers.BOT;

    static List<Row> rows() {
        return List.of(
                row(network.foodsharing.achievement.web.AchievementsController.class,
                        anon(get("/api/achievements"), 401),
                        as(FS, get("/api/users/" + FS.id() + "/achievements")),
                        as(FS, get("/api/achievements/00000051-0001-4000-8000-000000000003/awards"), 403)),
                guarded(network.foodsharing.activity.web.ActivityController.class, "/api/activity", FS, null),
                guarded(network.foodsharing.admin.web.AdminIntegrationsController.class, "/api/admin/integrations", ORGA, FS),
                guarded(network.foodsharing.admin.web.AdminJobsController.class, "/api/admin/jobs", ORGA, FS),
                guarded(network.foodsharing.admin.web.AdminMailController.class, "/api/admin/mail-outbox", ORGA, FS),
                guarded(network.foodsharing.admin.web.AdminSettingsController.class, "/api/admin/feature-toggles", ORGA, FS),
                guarded(network.foodsharing.admin.web.AdminUploadsController.class, "/api/admin/uploads/summary", ORGA, FS),
                row(network.foodsharing.admin.web.PublicSettingsController.class,
                        anon(get("/api/public/feature-toggles"), 200), anon(get("/api/public/feature-toggles"), 200), null),
                row(network.foodsharing.auth.web.AuthController.class,
                        anon(get("/api/auth/methods"), 401), as(FS, get("/api/auth/methods")), null),
                guarded(network.foodsharing.automation.web.AutomationController.class,
                        "/api/stores/" + S01 + "/automation", BIEB, FS),
                guarded(network.foodsharing.basket.web.BasketsController.class, "/api/baskets/mine", FS, null),
                guarded(network.foodsharing.calendar.web.CalendarController.class, "/api/calendar/token", FS, null),
                row(network.foodsharing.calendar.web.PublicCalendarController.class,
                        anon(get("/api/public/calendar/" + CALENDAR_TOKEN + ".ics"), 200),
                        anon(get("/api/public/calendar/" + CALENDAR_TOKEN + ".ics"), 200),
                        anon(get("/api/public/calendar/" + "f".repeat(64) + ".ics"), 404)),
                row(network.foodsharing.common.web.JobsController.class,
                        anon(test -> MockMvcRequestBuildersGet.of("/api/jobs/" + test.jobId), 401),
                        as(FS, test -> MockMvcRequestBuildersGet.of("/api/jobs/" + test.jobId)),
                        as(BIEB, test -> MockMvcRequestBuildersGet.of("/api/jobs/" + test.jobId), 404)),
                row(network.foodsharing.common.web.PublicController.class,
                        anon(get("/api/public/app-info"), 200), anon(get("/api/public/app-info"), 200), null),
                guarded(network.foodsharing.content.web.ContentController.class, "/api/content/pages", ORGA, FS),
                row(network.foodsharing.content.web.LegalController.class,
                        anon(get("/api/legal/consents"), 401), as(FS, get("/api/legal/consents")),
                        as(FS, get("/api/legal/PRIVACY_POLICY/versions"), 403)),
                row(network.foodsharing.content.web.PublicContentController.class,
                        anon(get("/api/public/blog"), 200), anon(get("/api/public/blog"), 200), null),
                row(network.foodsharing.content.web.PublicLegalController.class,
                        anon(get("/api/public/legal/PRIVACY_POLICY"), 200),
                        anon(get("/api/public/legal/PRIVACY_POLICY"), 200), null),
                row(network.foodsharing.dev.web.DevController.class,
                        anon(get("/api/dev/users"), 200), anon(get("/api/dev/users"), 200), null),
                row(network.foodsharing.event.web.EventsController.class,
                        anon(get("/api/events/mine"), 401), as(FS, get("/api/regions/" + R + "/events")),
                        as(SeedUsers.JUMPER, test -> delete("/api/events/00000044-0002-4000-8000-000000000001"), 403)),
                row(network.foodsharing.forum.web.ForumController.class,
                        anon(get("/api/regions/" + R + "/forum/threads"), 401),
                        as(FS, get("/api/regions/" + R + "/forum/threads")),
                        as(FS, get("/api/regions/" + R + "/moderation-log"), 403)),
                row(network.foodsharing.fsp.web.FoodSharePointsController.class,
                        anon(get("/api/food-share-points/mine"), 401),
                        as(FS, get("/api/regions/" + R + "/food-share-points")), null),
                row(network.foodsharing.fsp.web.FspCleaningController.class,
                        anon(get("/api/users/me/cleaning-shifts"), 401),
                        as(BIEB, get("/api/food-share-points/" + FSP01 + "/cleaning/regular-shifts")),
                        as(SeedUsers.JUMPER, test -> put("/api/food-share-points/" + FSP01 + "/cleaning/regular-shifts")
                                .contentType(MediaType.APPLICATION_JSON).content("[]"), 403)),
                row(network.foodsharing.history.web.HistoryController.class,
                        anon(get("/api/history/me"), 401), as(FS, get("/api/history/me")),
                        as(FS, get("/api/history"), 403)),
                guarded(network.foodsharing.intropickup.web.IntroPickupAdminController.class,
                        "/api/regions/" + R + "/admin/guides", BOT, FS),
                row(network.foodsharing.intropickup.web.IntroPickupGuideController.class,
                        anon(get("/api/guide/regions"), 401), as(SeedUsers.GUIDE, get("/api/guide/regions")),
                        as(FS, get("/api/regions/" + R + "/guide/bookings"), 403)),
                guarded(network.foodsharing.intropickup.web.IntroPickupsController.class, "/api/users/me/intro-pickups",
                        SeedUsers.EINAB1, null),
                row(network.foodsharing.intropickup.web.PublicIntroPickupsController.class,
                        anon(get("/api/public/regions/zuerich-stadt/intro-pickups"), 200),
                        anon(get("/api/public/regions/zuerich-stadt/intro-pickups"), 200),
                        anon(get("/api/public/intro-pickups/ical/not-a-token"), 404)),
                row(network.foodsharing.mailbox.web.MailboxesController.class,
                        anon(get("/api/mailboxes"), 401), as(FS, get("/api/mailboxes")),
                        as(FS, get("/api/admin/mailboxes"), 403)),
                row(network.foodsharing.mailbox.web.PublicMailboxController.class,
                        anon(postJson("/api/public/mailbox/inbound/smoke-mailbox-secret", "{\"rawMime\":\"" + MIME + "\"}"), 202),
                        anon(postJson("/api/public/mailbox/inbound/smoke-mailbox-secret", "{\"rawMime\":\"" + MIME + "\"}"), 202),
                        anon(postJson("/api/public/mailbox/inbound/wrong-secret", "{\"rawMime\":\"" + MIME + "\"}"), 404)),
                row(network.foodsharing.map.web.MapController.class,
                        anon(get("/api/map/markers/stores"), 401), as(FS, get("/api/map/markers/stores")),
                        as(FS, get("/api/map/markers/users?regionId=" + R), 403)),
                row(network.foodsharing.map.web.PublicMapController.class,
                        anon(get("/api/public/map/markers/regions"), 200), anon(get("/api/public/map/markers/regions"), 200),
                        null),
                row(network.foodsharing.messaging.web.MessagingController.class,
                        anon(get("/api/conversations"), 401), as(FS, get("/api/conversations")),
                        as(SeedUsers.GUIDE, get("/api/stores/" + S01 + "/conversations"), 403)),
                guarded(network.foodsharing.notification.newsletter.web.NewslettersController.class, "/api/newsletters",
                        ORGA, FS),
                row(network.foodsharing.notification.newsletter.web.PublicNewsletterController.class,
                        anon(test -> MockMvcRequestBuildersGet.of("/api/public/newsletter/unsubscribe/" + test.newsletterToken), 200),
                        anon(test -> MockMvcRequestBuildersGet.of("/api/public/newsletter/unsubscribe/" + test.newsletterToken), 200),
                        anon(get("/api/public/newsletter/unsubscribe/" + "A".repeat(43)), 404)),
                guarded(network.foodsharing.notification.push.web.PushController.class, "/api/push/subscriptions", FS, null),
                row(network.foodsharing.notification.telegram.web.PublicTelegramWebhookController.class,
                        anon(postJson("/api/public/telegram/webhook/smoke-telegram-secret", "{\"update_id\":1}"), 204),
                        anon(postJson("/api/public/telegram/webhook/smoke-telegram-secret", "{\"update_id\":2}"), 204),
                        anon(postJson("/api/public/telegram/webhook/wrong-secret", "{\"update_id\":3}"), 404)),
                row(network.foodsharing.notification.telegram.web.TelegramController.class,
                        anon(get("/api/telegram/chats"), 401), as(BOT, get("/api/regions/" + R + "/telegram-chats")),
                        as(FS, get("/api/regions/" + R + "/telegram-chats"), 403)),
                guarded(network.foodsharing.notification.web.BellsController.class, "/api/bells", FS, null),
                guarded(network.foodsharing.oauthserver.web.AdminOAuthClientsController.class, "/api/admin/oauth-clients",
                        ORGA, FS),
                row(network.foodsharing.oauthserver.web.OAuthConsentController.class,
                        anon(test -> MockMvcRequestBuildersGet.of("/api/oauth/authorize-requests/" + test.consentRequestId), 401),
                        as(SeedUsers.JUMPER, test -> MockMvcRequestBuildersGet.of("/api/oauth/authorize-requests/" + test.consentRequestId)),
                        as(FS, test -> MockMvcRequestBuildersGet.of("/api/oauth/authorize-requests/" + test.consentRequestId), 403)),
                row(network.foodsharing.onboarding.web.OnboardingController.class,
                        anon(get("/api/onboarding/journey"), 401), as(SeedUsers.NEWBIE, get("/api/onboarding/journey")),
                        as(FS, postJson("/api/onboarding/regions/" + R + "/users/" + SeedUsers.JUMPER.id()
                                + "/steps/newcomer-meeting/confirm", "{}"), 403)),
                row(network.foodsharing.onboarding.web.PublicOnboardingController.class,
                        anon(get("/api/public/onboarding/explainer"), 200), anon(get("/api/public/onboarding/explainer"), 200),
                        null),
                row(network.foodsharing.pickup.web.PickupsController.class,
                        anon(get("/api/users/me/pickups"), 401), as(FS, get("/api/stores/" + S01 + "/regular-pickups")),
                        as(SeedUsers.GUIDE, get("/api/stores/" + S01 + "/regular-pickups"), 403)),
                row(network.foodsharing.poll.web.PollsController.class,
                        anon(get("/api/polls/mine"), 401), as(FS, get("/api/regions/" + R + "/polls")),
                        as(FS, get("/api/regions/" + BERLIN + "/polls"), 403)),
                guarded(network.foodsharing.profile.web.ProfilesController.class, "/api/users/me/buddies", FS, null),
                row(network.foodsharing.quiz.web.QuizzesController.class,
                        anon(get("/api/quizzes"), 401), as(SeedUsers.QUIZ, get("/api/quizzes")),
                        as(FS, get("/api/quizzes/FOODSAVER/questions"), 403)),
                guarded(network.foodsharing.region.web.AdminAdminsController.class, "/api/admin/admins", ORGA, FS),
                row(network.foodsharing.region.web.PublicRegionsController.class,
                        anon(get("/api/public/regions/tree"), 200), anon(get("/api/public/regions/tree"), 200), null),
                guarded(network.foodsharing.region.web.RegionAdminController.class, "/api/admin/regions", ORGA, FS),
                row(network.foodsharing.region.web.RegionsController.class,
                        anon(get("/api/regions/mine"), 401), as(FS, get("/api/regions/mine")),
                        as(FS, get("/api/regions/" + BERLIN + "/members"), 403)),
                row(network.foodsharing.region.web.WorkingGroupsController.class,
                        anon(get("/api/groups/mine"), 401), as(FS, get("/api/groups/mine")),
                        as(FS, get("/api/groups/" + REPORT_GROUP + "/applications"), 403)),
                guarded(network.foodsharing.report.web.ReportsController.class, "/api/regions/" + R + "/reports",
                        SeedUsers.REPORTS, FS),
                guarded(network.foodsharing.resource.web.ResourcesController.class, "/api/resources/mine", FS, null),
                guarded(network.foodsharing.search.web.SearchController.class, "/api/search?q=coop", FS, null),
                row(network.foodsharing.statistics.web.StatisticsController.class,
                        anon(get("/api/regions/" + R + "/statistics/gender"), 401),
                        as(FS, get("/api/regions/" + R + "/statistics/gender")),
                        as(FS, get("/api/regions/" + BERLIN + "/statistics/gender"), 403)),
                row(network.foodsharing.store.web.StoreAdminController.class,
                        anon(postJson("/api/regions/" + R + "/stores/import", importBody()), 401),
                        as(ORGA, postJson("/api/regions/" + R + "/stores/import", importBody())),
                        as(FS, postJson("/api/regions/" + R + "/stores/import", importBody()), 403)),
                guarded(network.foodsharing.store.web.StoreCategoriesController.class, "/api/store-categories", FS, null),
                guarded(network.foodsharing.store.web.StoreChainsController.class, "/api/store-chains", FS, SeedUsers.NEWBIE),
                guarded(network.foodsharing.store.web.StoreTeamController.class,
                        "/api/stores/" + S01 + "/invite-candidates?search=mu", BIEB, FS),
                row(network.foodsharing.store.web.StoresController.class,
                        anon(get("/api/users/me/stores"), 401), as(FS, get("/api/stores/" + S01)),
                        as(FS, get("/api/stores/" + S01 + "/log"), 403)),
                row(network.foodsharing.support.web.PublicSupportController.class,
                        anon(postJson("/api/public/support/tickets", "{\"subject\":\"Smoke\",\"body\":\"Hello\","
                                + "\"email\":\"smoke@example.org\",\"firstName\":\"Smoke\"}"), 201),
                        anon(postJson("/api/public/support/tickets", "{\"subject\":\"Smoke\",\"body\":\"Hello\","
                                + "\"email\":\"smoke@example.org\",\"firstName\":\"Smoke\"}"), 201), null),
                row(network.foodsharing.support.web.SupportController.class,
                        anon(postJson("/api/support/tickets", "{\"subject\":\"Smoke\",\"body\":\"Hello\"}"), 401),
                        as(FS, postJson("/api/support/tickets", "{\"subject\":\"Smoke\",\"body\":\"Hello\"}"), 201), null),
                guarded(network.foodsharing.survey.web.SurveysController.class, "/api/surveys", ORGA, FS),
                row(network.foodsharing.upload.web.UploadsController.class,
                        anon(get("/api/uploads/" + AVATAR), 200), anon(get("/api/uploads/" + AVATAR), 200), null),
                row(network.foodsharing.user.web.PublicUsersController.class,
                        anon(postJson("/api/public/users/email-change/abort", "{\"token\":\"no-such-token\"}"), 400),
                        anon(postJson("/api/public/users/email-change/abort",
                                "{\"token\":\"seed-email-change-abort-profile\"}"), 204), null),
                guarded(network.foodsharing.user.web.UserAdminController.class, "/api/admin/users/bounced", ORGA, FS),
                guarded(network.foodsharing.user.web.UsersController.class, "/api/users/me", FS, null),
                row(network.foodsharing.verification.web.PassportsController.class,
                        anon(get("/api/users/me/passport"), 401), as(FS, get("/api/users/me/passport")),
                        as(FS, get("/api/regions/" + R + "/passport-requests"), 403)),
                guarded(network.foodsharing.verification.web.VerificationController.class,
                        "/api/regions/" + R + "/verification/queue", BOT, FS));
    }

    /**
     * Controllers this context cannot load, with the test that covers them instead. The persona panel only exists in
     * the {@code staging} profile with a panel password.
     */
    private static final Map<String, String> ELSEWHERE = Map.of(
            "network.foodsharing.persona.web.PersonaController", "persona/PersonaPanelStagingTest");

    private static String importBody() {
        return "{\"dryRun\":true,\"csv\":" + quote(CSV) + "}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static Stream<Row> deniedRows() {
        return rows().stream().filter(row -> row.denied() != null);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProgressTracker tracker;

    @Autowired
    private NewsletterTokenService newsletterTokens;

    private final Map<SeedUsers, Cookie> sessions = new EnumMap<>(SeedUsers.class);

    private UUID jobId;

    private String newsletterToken;

    private String consentRequestId;

    @org.junit.jupiter.api.BeforeAll
    void prepare() throws Exception {
        jobId = tracker.start(FS.id(), 1);
        newsletterToken = UriComponentsBuilder.fromUriString(newsletterTokens.unsubscribeUrl(SeedUsers.JUMPER.id()))
                .build().getPathSegments().getLast();
        String location = mvc.perform(MockMvcRequestBuildersGet.of("/oauth/authorize").cookie(session(SeedUsers.JUMPER))
                        .param("response_type", "code").param("client_id", "mobile-dev")
                        .param("redirect_uri", "http://localhost:8080/dev/oauth-callback").param("scope", "openid profile")
                        .param("state", "smoke").param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("consent redirect").startsWith("/oauth/consent?request=");
        consentRequestId = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("request");
    }

    private Cookie session(SeedUsers user) throws Exception {
        Cookie cookie = sessions.get(user);
        if (cookie == null) {
            MockHttpServletResponse response = mvc.perform(post("/api/dev/login-as")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + user.id() + "\"}"))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as("login as " + user).isEqualTo(200);
            cookie = Arrays.stream(response.getCookies()).filter(c -> c.getName().equals("fs_session")).findFirst()
                    .orElseThrow();
            sessions.put(user, cookie);
        }
        return cookie;
    }

    private void check(Call call, String what) throws Exception {
        MockHttpServletRequestBuilder request = call.request().apply(this).with(csrf());
        if (call.user() != null) {
            request.cookie(session(call.user()));
        }
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(what + " as " + (call.user() == null ? "anonymous" : call.user())
                + " got " + response.getStatus() + ": " + response.getContentAsString()).isEqualTo(call.status());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void anonymous(Row row) throws Exception {
        check(row.anonymous(), "anonymous");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void allowedRoleGets2xx(Row row) throws Exception {
        assertThat(row.allowed().status()).as("allowed status").isBetween(200, 299);
        check(row.allowed(), "allowed");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deniedRows")
    void otherRoleIsRefused(Row row) throws Exception {
        check(row.denied(), "denied");
    }

    @Test
    void everyRestControllerHasASmokeRow() {
        // every @RestController class, whatever its @Profile or @Conditional says
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(MetadataReader reader) {
                return reader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())
                        && !isTestClass(reader);
            }
        };
        Set<String> controllers = scanner.findCandidateComponents("network.foodsharing").stream()
                .map(bean -> bean.getBeanClassName()).collect(Collectors.toCollection(TreeSet::new));
        Set<String> covered = rows().stream().map(row -> row.controller().getName())
                .collect(Collectors.toCollection(TreeSet::new));
        covered.addAll(ELSEWHERE.keySet());
        assertThat(controllers).isNotEmpty().containsExactlyInAnyOrderElementsOf(covered);
        assertThat(rows().stream().map(Row::controller).distinct().count()).as("one row per controller")
                .isEqualTo(rows().size());
    }

    private static boolean isTestClass(MetadataReader reader) {
        try {
            return reader.getResource().getURL().toString().contains("/test-classes/");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** {@code get(String)} of MockMvc under another name (the static {@link #get(String)} above returns a function). */
    private static final class MockMvcRequestBuildersGet {
        static MockHttpServletRequestBuilder of(String path) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
        }
    }
}
