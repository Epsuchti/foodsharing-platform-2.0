package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import network.foodsharing.testsupport.DataInventory;
import network.foodsharing.testsupport.DataInventory.OnDeletion;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A13/A21/A40: GDPR export, no-automatic-delete flag, self and ORGA deletion with anonymisation and archive. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deletion-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountDeletionIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<AccountDeletionContributor> deletionContributors;

    @Autowired
    private List<DataExportContributor> exportContributors;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void exportContainsEverySection() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String body = mvc.perform(post("/api/users/me/export").with(csrf()).cookie(bieb))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.jobId");
        byte[] zip = mvc.perform(get("/api/jobs/" + jobId + "/result").cookie(bieb)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertThat(names).contains("profile.json", "settings.json", "history.json", "sessions.json",
                "notifications.json", "regions.json", "stores.json", "pickups.json", "community.json",
                "calendar.json", "events.json", "mailboxes.json", "messages.json", "forum.json", "polls.json",
                "surveys.json", "quiz.json", "onboarding.json", "verification.json", "intropickups.json",
                "automation.json", "baskets.json", "reports.json", "achievements.json")
                .anyMatch(name -> name.startsWith("uploads/") && name.endsWith("avatar-bieb.png"));
        // every personal-data module of the data inventory (D17.3) appears in the zip
        Set<String> inventoryFiles = DataInventory.rows().stream().map(DataInventory.Row::export)
                .filter(file -> file != null && file.endsWith(".json")).collect(Collectors.toCollection(java.util.TreeSet::new));
        assertThat(inventoryFiles).hasSizeGreaterThan(20);
        assertThat(names).containsAll(inventoryFiles);
        mvc.perform(post("/api/users/me/export").with(csrf()).cookie(bieb)).andExpect(status().isTooManyRequests());
    }

    /** Every module that holds personal data takes part in both the deletion and the export (A40). */
    @Test
    void everyPersonalDataModuleContributes() {
        Set<String> expected = Set.of("auth", "notification", "region", "store", "pickup", "mailbox", "event",
                "calendar", "profile", "messaging", "forum", "poll", "survey", "quiz", "onboarding", "verification",
                "intropickup", "automation", "basket", "report", "achievement");
        assertThat(modules(deletionContributors)).containsAll(expected);
        assertThat(modules(exportContributors)).containsAll(expected);
    }

    private static Set<String> modules(List<?> beans) {
        return beans.stream().map(bean -> org.springframework.aop.support.AopUtils.getTargetClass(bean).getPackageName()
                .split("\\.")[2]).collect(Collectors.toSet());
    }

    @Test
    void selfDeletionAnonymisesAndKeepsAuthoredContent() throws Exception {
        String id = SeedUsers.FOODSAVER.id().toString();
        mvc.perform(patch("/api/admin/users/" + id + "/no-automatic-delete").with(csrf())
                        .cookie(login(SeedUsers.JUMPER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + id + "/no-automatic-delete").with(csrf())
                        .cookie(login(SeedUsers.ORGA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select no_automatic_delete from user_account where id = ?", Boolean.class,
                SeedUsers.FOODSAVER.id())).isTrue();

        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(delete("/api/users/me").with(csrf()).cookie(foodsaver).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ich ziehe weg\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").cookie(foodsaver)).andExpect(status().isUnauthorized());

        Map<String, Object> row = jdbc.queryForMap("select email, display_name, active, mobile_phone, avatar_url, "
                + "deleted_at, delete_reason, role from user_account where id = ?", SeedUsers.FOODSAVER.id());
        assertThat(row.get("EMAIL")).isEqualTo("deleted-" + id + "@deleted.invalid");
        assertThat(row.get("DISPLAY_NAME")).isEqualTo("Gelöschtes Mitglied");
        assertThat(row.get("ACTIVE")).isEqualTo(false);
        assertThat(row.get("MOBILE_PHONE")).isNull();
        assertThat(row.get("AVATAR_URL")).isNull();
        assertThat(row.get("DELETED_AT")).isNotNull();
        assertThat(row.get("DELETE_REASON")).isEqualTo("Ich ziehe weg");
        assertThat(row.get("ROLE")).isEqualTo("FOODSHARER");
        assertThat(jdbc.queryForObject("select count(*) from region_membership where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from store_membership where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from auth_identity where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from calendar_token where user_id = ?", Integer.class,
                SeedUsers.FOODSAVER.id())).isZero();
        for (String table : List.of("forum_follow", "forum_thread_follow", "forum_post_reaction", "poll_vote", "quiz_session",
                "learning_progress", "user_onboarding_step_done", "passport_issue", "guide_profile")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class,
                    SeedUsers.FOODSAVER.id())).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select author_id from forum_post where id = '00000043-0002-4000-8000-000000000002'",
                String.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("select snapshot_json from user_archive where user_id = ?", String.class,
                SeedUsers.FOODSAVER.id())).contains("foodsaver@local");
        assertThat(jdbc.queryForObject("select count(*) from history_entry where subject_id = ? and action = "
                + "'ACCOUNT_DELETED' and reason = 'Ich ziehe weg'", Integer.class, SeedUsers.FOODSAVER.id()))
                .isEqualTo(1);
        mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + id + "\"}")).andExpect(status().is4xxClientError());
    }

    @Test
    void businessCardForOwnRegionAndRole() throws Exception {
        String region = jdbc.queryForObject("select home_region_id from user_account where id = ?", String.class,
                SeedUsers.BIEB.id());
        Cookie bieb = login(SeedUsers.BIEB);
        byte[] pdf = mvc.perform(get("/api/users/me/business-card").param("role", "STORE_MANAGER")
                        .param("regionId", region).cookie(bieb))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mvc.perform(get("/api/users/me/business-card").param("role", "AMBASSADOR").param("regionId", region)
                .cookie(bieb)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/business-card").param("role", "FOODSAVER")
                .param("regionId", java.util.UUID.randomUUID().toString()).cookie(bieb))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOrgaDeletesOthers() throws Exception {
        String url = "/api/admin/users/" + SeedUsers.SLEEPY.id();
        mvc.perform(delete(url).with(csrf()).cookie(login(SeedUsers.WELCOME))).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(csrf()).cookie(login(SeedUsers.ORGA))).andExpect(status().isNoContent());
        mvc.perform(delete(url).with(csrf()).cookie(login(SeedUsers.ORGA))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select deleted_by from user_archive where user_id = ?", String.class,
                SeedUsers.SLEEPY.id())).isEqualTo(SeedUsers.ORGA.id().toString());
    }

    /**
     * D17.3: deletes every seed account but ORGA and walks every column that references a user (foreign keys to
     * {@code user_account} plus id columns by naming convention, {@link DataInventory#userReferences}). Each one is
     * classified in {@code docs/privacy/data-inventory.md}; every row that pointed at a deleted account must end as
     * documented: DELETED = row gone, ANONYMISED = row kept with another value, KEPT = row unchanged. A row removed
     * because another of its columns is DELETED does not count against a KEPT column.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void deletionHandlesEveryUserReferenceAsDocumented() throws Exception {
        Map<String, Set<OnDeletion>> documented = new TreeMap<>();
        DataInventory.rows().stream().filter(row -> !row.onDeletion().isEmpty())
                .forEach(row -> documented.put(row.key(), row.onDeletion()));
        Set<String> references = DataInventory.userReferences(jdbc);
        assertThat(documented.keySet()).as("user references classified in %s", DataInventory.FILE)
                .containsAll(references);
        Map<String, List<String>> byTable = new TreeMap<>();
        references.forEach(ref -> byTable.computeIfAbsent(ref.substring(0, ref.indexOf('.')), t -> new ArrayList<>())
                .add(ref.substring(ref.indexOf('.') + 1)));

        List<UUID> victims = Arrays.stream(SeedUsers.values()).filter(user -> user != SeedUsers.ORGA)
                .map(SeedUsers::id).filter(id -> jdbc.queryForObject("select deleted_at from user_account where id = ?",
                        java.sql.Timestamp.class, id) == null).toList();
        String in = victims.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        // table -> pk -> column -> victim the column pointed at
        Map<String, Map<Object, Map<String, String>>> before = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (var table : byTable.entrySet()) {
            String pk = DataInventory.primaryKey(jdbc, table.getKey());
            for (String column : table.getValue()) {
                counts.put(table.getKey() + "." + column, jdbc.queryForObject("select count(*) from " + table.getKey()
                        + " where " + column + " in (" + in + ")", Integer.class));
                if (pk != null && !pk.equals(column)) {
                    jdbc.queryForList("select " + pk + " p, " + column + " c from " + table.getKey() + " where "
                            + column + " in (" + in + ")").forEach(row -> before.computeIfAbsent(table.getKey(),
                            t -> new LinkedHashMap<>()).computeIfAbsent(row.get("p"), p -> new TreeMap<>())
                            .put(column, String.valueOf(row.get("c"))));
                }
            }
        }

        Cookie orga = login(SeedUsers.ORGA);
        for (UUID victim : victims) {
            mvc.perform(delete("/api/admin/users/" + victim).with(csrf()).cookie(orga)).andExpect(status().isNoContent());
        }

        Map<String, Set<OnDeletion>> observed = new TreeMap<>();
        references.forEach(ref -> observed.put(ref, new java.util.TreeSet<>()));
        for (var table : byTable.entrySet()) {
            String pk = DataInventory.primaryKey(jdbc, table.getKey());
            for (var row : before.getOrDefault(table.getKey(), Map.of()).entrySet()) {
                List<Map<String, Object>> now = jdbc.queryForList("select * from " + table.getKey() + " where " + pk
                        + " = ?", row.getKey());
                for (var ref : row.getValue().entrySet()) {
                    String key = table.getKey() + "." + ref.getKey();
                    if (now.isEmpty()) {
                        boolean explained = row.getValue().keySet().stream().anyMatch(other -> !other.equals(ref.getKey())
                                && documented.getOrDefault(table.getKey() + "." + other, Set.of())
                                .contains(OnDeletion.DELETED));
                        if (!explained || documented.getOrDefault(key, Set.of()).contains(OnDeletion.DELETED)) {
                            observed.get(key).add(OnDeletion.DELETED);
                        }
                    } else {
                        observed.get(key).add(ref.getValue().equals(String.valueOf(now.getFirst().get(ref.getKey())))
                                ? OnDeletion.KEPT : OnDeletion.ANONYMISED);
                    }
                }
            }
            if (pk == null) {
                for (String column : table.getValue()) {
                    String key = table.getKey() + "." + column;
                    int after = jdbc.queryForObject("select count(*) from " + table.getKey() + " where " + column
                            + " in (" + in + ")", Integer.class);
                    if (counts.get(key) > 0) {
                        observed.get(key).add(after == 0 ? OnDeletion.DELETED : OnDeletion.KEPT);
                    }
                }
            }
        }
        List<String> report = new ArrayList<>();
        observed.forEach((ref, outcomes) -> report.add(ref + " documented=" + documented.get(ref) + " observed="
                + outcomes + " rows=" + counts.get(ref)));
        Files.createDirectories(Path.of("target", "privacy"));
        Files.write(Path.of("target", "privacy", "deletion-walk.txt"), report);

        observed.forEach((ref, outcomes) -> assertThat(documented.get(ref)).as(ref).containsAll(outcomes));
        // the walk only means something with data: most references have rows of a deleted seed account
        assertThat(counts.values().stream().filter(count -> count > 0).count()).isGreaterThan(references.size() / 2);
        // baskets keep no contact data or text of a deleted owner
        assertThat(jdbc.queryForObject("select count(*) from basket where owner_id in (" + in + ") and (phone is not null "
                + "or mobile is not null or description <> '')", Integer.class)).isZero();
        // ACCOUNT_LIFETIME history about a deleted account keeps no values (plan §10.1)
        assertThat(jdbc.queryForObject("select count(*) from history_entry where subject_type = 'USER' and subject_id in ("
                + in + ") and action = 'PHONE_CHANGED' and (old_value is not null or new_value is not null)",
                Integer.class)).isZero();
    }
}
