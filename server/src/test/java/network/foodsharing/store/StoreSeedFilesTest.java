package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;

/**
 * The store seed files (docs/seed-ids.md, B2) without a database: 25 stores inside Zürich covering every cooperation
 * and team status, the metadata tables and the team constellations the store and pickup journeys start from. That
 * the files load is proven by every seeded context (LocalProfileSmokeIT, LiquibasePostgresIT). Replaces StoreSeedIT and
 * the seed checks of StoreApiIT and StoreWallApiIT.
 */
class StoreSeedFilesTest {

    private static final String STORE_PREFIX = "00000020-0001-4000-8000-0000000000";

    private static List<Map<String, String>> csv(String file) throws IOException {
        try (InputStream in = StoreSeedFilesTest.class.getResourceAsStream("/db/seed/store/" + file)) {
            assertThat(in).as(file).isNotNull();
            List<List<String>> rows = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<String> header = rows.getFirst();
            List<Map<String, String>> result = new ArrayList<>();
            for (List<String> row : rows.subList(1, rows.size())) {
                Map<String, String> values = new HashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    String value = i < row.size() ? row.get(i) : "";
                    values.put(header.get(i), "NULL".equals(value) ? "" : value);
                }
                result.add(values);
            }
            return result;
        }
    }

    /** RFC 4180 as Liquibase reads it: quoted fields may hold commas, quotes and line breaks. */
    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isEmpty())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static long count(List<Map<String, String>> rows, String column, String value) {
        return rows.stream().filter(r -> value.equals(r.get(column))).count();
    }

    @Test
    void storesCoverEveryStatusInsideZurich() throws IOException {
        List<Map<String, String>> stores = csv("store.csv").stream()
                .filter(r -> r.get("id").startsWith(STORE_PREFIX)).toList();
        assertThat(stores).hasSize(25);
        for (CooperationStatus status : CooperationStatus.values()) {
            assertThat(count(stores, "cooperation_status", status.name())).as(status.name()).isPositive();
        }
        assertThat(count(stores, "cooperation_status", "COOPERATION_ESTABLISHED")).isEqualTo(18);
        for (TeamSearchStatus status : TeamSearchStatus.values()) {
            assertThat(count(stores, "team_status", status.name())).as(status.name()).isPositive();
        }
        for (Map<String, String> store : stores) {
            String name = store.get("name");
            assertThat(Double.parseDouble(store.get("latitude"))).as(name).isBetween(47.3, 47.6);
            assertThat(Double.parseDouble(store.get("longitude"))).as(name).isBetween(8.4, 8.8);
            assertThat(store.get("deleted_at")).as(name).isEmpty();
            assertThat(Integer.parseInt(store.get("prefetch_days"))).as(name).isBetween(1, 42);
        }
    }

    @Test
    void metadataTables() throws IOException {
        assertThat(csv("store_category.csv")).hasSizeGreaterThanOrEqualTo(8);
        assertThat(csv("grocery.csv")).hasSizeGreaterThanOrEqualTo(8);
        assertThat(csv("pickup_weight_category.csv")).hasSize(9);
        assertThat(csv("store_chain.csv")).hasSizeGreaterThanOrEqualTo(6);
        assertThat(count(csv("store_chain_kam.csv"), "user_id", SeedUsers.BIEB.id().toString())).isPositive();
    }

    @Test
    void teamConstellations() throws IOException {
        List<Map<String, String>> team = csv("store_membership.csv");
        for (StoreMembershipStatus status : StoreMembershipStatus.values()) {
            assertThat(count(team, "status", status.name())).as(status.name()).isPositive();
        }
        assertThat(team.stream().filter(m -> m.get("user_id").equals(SeedUsers.BIEB.id().toString())
                && m.get("manager").equals("true")).count()).isGreaterThanOrEqualTo(6);
        assertThat(managers(team, "10")).isEqualTo(2);
        assertThat(managers(team, "11")).isEqualTo(3);
        Set<String> withTeam = team.stream().map(m -> m.get("store_id")).collect(Collectors.toSet());
        Set<String> withManager = team.stream().filter(m -> m.get("manager").equals("true"))
                .map(m -> m.get("store_id")).collect(Collectors.toSet());
        assertThat(withManager).as("every store with a team has a manager").isEqualTo(withTeam);
        assertThat(team.stream().filter(m -> m.get("user_id").equals(SeedUsers.JUMPER.id().toString())
                && m.get("status").equals("JUMPER")).count()).isEqualTo(2);
        assertThat(count(team, "user_id", SeedUsers.ORGA.id().toString())).isZero();
    }

    @Test
    void everyEstablishedStoreHasItsCooperationMilestoneOnTheWall() throws IOException {
        List<Map<String, String>> posts = csv("store_wall_post.csv");
        assertThat(posts.stream().map(p -> p.get("store_id")).distinct().count()).isEqualTo(18);
        assertThat(count(posts, "milestone", "COOPERATION_STARTED")).isEqualTo(18);
    }

    private static long managers(List<Map<String, String>> team, String store) {
        return team.stream().filter(m -> m.get("store_id").equals(STORE_PREFIX + store)
                && m.get("manager").equals("true")).count();
    }
}
