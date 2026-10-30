package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import network.foodsharing.testsupport.DataInventory;
import network.foodsharing.testsupport.DataInventory.Row;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.SharedJpaSliceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D17.3: {@code docs/privacy/data-inventory.md} documents every column that references a user (foreign keys to
 * {@code user_account}, id columns named like a user reference, JPA attributes of entities named like one) with its
 * deletion outcome; it names no column that does not exist; every documented table with an expiry column is listed
 * with its remover in {@code docs/privacy/retention.md}.
 */
@JpaSliceTest
@org.springframework.context.annotation.Import(SharedJpaSliceConfig.class)
class DataInventoryTest {

    private static final Pattern USER_ATTRIBUTE = Pattern.compile(
            "^(.*_)?(user_id|author_id|actor_id|sender_id|recipient_id|requester_id|guide_id)$|^.+_by$");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    @Test
    void everyUserReferenceIsDocumentedWithItsDeletionOutcome() {
        Map<String, Row> documented = documented();
        Set<String> undocumented = new TreeSet<>();
        for (String reference : DataInventory.userReferences(jdbc)) {
            Row row = documented.get(reference);
            if (row == null || row.onDeletion().isEmpty()) {
                undocumented.add(reference);
            }
        }
        assertThat(undocumented).as("user references missing (or without deleted/anonymised/kept) in %s",
                DataInventory.FILE).isEmpty();
    }

    @Test
    void everyJpaAttributeReferencingAUserIsDocumented() {
        Map<String, Row> documented = documented();
        Set<String> undocumented = new TreeSet<>();
        for (EntityType<?> entity : em.getMetamodel().getEntities()) {
            String table = table(entity);
            if (table.endsWith("_i18n")) {
                continue;
            }
            for (Attribute<?, ?> attribute : entity.getAttributes()) {
                String column = snake(attribute.getName());
                boolean userEntity = attribute.getJavaType().getSimpleName().equals("User");
                boolean userId = attribute.getJavaType() == UUID.class && USER_ATTRIBUTE.matcher(column).matches();
                if (userEntity) {
                    column = column + "_id";
                }
                if ((userEntity || userId) && !DataInventory.AUDIT_COLUMNS.contains(column)
                        && !documented.containsKey(table + "." + column)) {
                    undocumented.add(entity.getJavaType().getSimpleName() + "." + attribute.getName() + " -> "
                            + table + "." + column);
                }
            }
        }
        assertThat(undocumented).as("JPA attributes referencing a user missing in %s", DataInventory.FILE).isEmpty();
    }

    @Test
    void documentedColumnsExist() {
        Set<String> columns = jdbc.queryForList("select table_name t, column_name c from information_schema.columns "
                        + "where lower(table_schema) = 'public'").stream()
                .map(r -> (r.get("t") + "." + r.get("c")).toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> stale = new TreeSet<>();
        for (Row row : DataInventory.rows()) {
            if (row.table().equals("*")) {
                assertThat(DataInventory.AUDIT_COLUMNS).contains(row.column());
            } else if (!columns.contains(row.key())) {
                stale.add(row.key());
            }
        }
        assertThat(stale).as("columns in %s that do not exist", DataInventory.FILE).isEmpty();
        assertThat(DataInventory.rows()).filteredOn(row -> row.table().equals("*")).extracting(Row::column)
                .containsExactlyInAnyOrderElementsOf(DataInventory.AUDIT_COLUMNS);
    }

    @Test
    void everyExpiringPersonalDataTableHasARemover() throws Exception {
        Set<String> tables = DataInventory.rows().stream().map(Row::table).collect(Collectors.toSet());
        Set<String> expiring = new TreeSet<>();
        jdbc.queryForList("select table_name from information_schema.columns where lower(table_schema) = 'public' "
                        + "and lower(column_name) in ('expires_at', 'until_at')", String.class)
                .forEach(table -> expiring.add(table.toLowerCase(Locale.ROOT)));
        expiring.retainAll(tables);
        String retention = String.join("\n", RetentionTableTest.section("Personal data outside the history"));
        assertThat(expiring).allSatisfy(table -> assertThat(retention).as("%s in %s", table, RetentionTableTest.FILE)
                .contains("| `" + table + "` |"));
    }

    private static Map<String, Row> documented() {
        Map<String, Row> result = new TreeMap<>();
        DataInventory.rows().forEach(row -> result.put(row.key(), row));
        return result;
    }

    private static String table(EntityType<?> entity) {
        Table table = entity.getJavaType().getAnnotation(Table.class);
        return table != null && !table.name().isEmpty() ? table.name() : snake(entity.getName());
    }

    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
