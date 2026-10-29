package network.foodsharing.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads {@code docs/privacy/data-inventory.md} (D17.3) and finds every column that references a user in the live
 * schema, so {@code DataInventoryTest} and {@code AccountDeletionIT} check the same list.
 */
public final class DataInventory {

    public static final Path FILE = Path.of("..", "docs", "privacy", "data-inventory.md");

    /** Classification of a user reference on account deletion. */
    public enum OnDeletion {
        /** The row is removed. */
        DELETED,
        /** The row stays, the reference is cleared or replaced. */
        ANONYMISED,
        /** The row stays and keeps pointing at the anonymised {@code user_account} row. */
        KEPT
    }

    /**
     * One documented column. {@code onDeletion} lists the allowed outcomes (several when the module decides per row,
     * e.g. past pickups kept, future ones deleted); empty for personal data that does not reference a user.
     */
    public record Row(String table, String column, String module, Set<OnDeletion> onDeletion, String export) {

        public String key() {
            return table + "." + column;
        }
    }

    /** Audit columns of every entity (BaseEntity); documented once with the table {@code *}. */
    public static final Set<String> AUDIT_COLUMNS = Set.of("created_by", "updated_by");

    /** UUID columns without a foreign key that hold a user id by naming convention. */
    private static final Pattern USER_COLUMN = Pattern.compile(
            "^(.*_)?(user_id|author_id|actor_id|owner_id|sender_id|recipient_id|requester_id|guide_id)$|^.+_by$");

    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private DataInventory() {
    }

    public static List<Row> rows() {
        List<String> lines;
        try {
            lines = Files.readAllLines(FILE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Row> rows = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String[] cells = Arrays.stream(line.substring(1, line.length() - 1).split("\\|", -1))
                    .map(String::strip).toArray(String[]::new);
            String table = code(cells[0]);
            String module = cells.length > 2 ? cells[2] : "";
            Set<OnDeletion> onDeletion = cells.length > 4 ? onDeletion(cells[4]) : Set.of();
            String export = cells.length > 5 ? code(cells[5]) : null;
            var columns = CODE.matcher(cells[1]);
            while (columns.find()) {
                rows.add(new Row(table, columns.group(1), module, onDeletion, export));
            }
        }
        return rows;
    }

    private static String code(String cell) {
        var matcher = CODE.matcher(cell);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** The outcome words before the first {@code :} (the rest of the cell explains). */
    private static Set<OnDeletion> onDeletion(String cell) {
        String head = cell.split(":", 2)[0];
        Set<OnDeletion> result = java.util.EnumSet.noneOf(OnDeletion.class);
        for (String word : head.split("[^a-zA-Z]+")) {
            Arrays.stream(OnDeletion.values()).filter(v -> v.name().equalsIgnoreCase(word)).forEach(result::add);
        }
        return result;
    }

    /** {@code table.column} of every foreign key to {@code user_account}. */
    public static Set<String> foreignKeysToUser(JdbcTemplate jdbc) {
        Set<String> result = new TreeSet<>();
        jdbc.queryForList("select kcu.table_name t, kcu.column_name c from information_schema.referential_constraints rc "
                        + "join information_schema.key_column_usage kcu on kcu.constraint_name = rc.constraint_name "
                        + "and kcu.constraint_schema = rc.constraint_schema "
                        + "join information_schema.table_constraints pk on pk.constraint_name = rc.unique_constraint_name "
                        + "and pk.constraint_schema = rc.unique_constraint_schema "
                        + "where lower(pk.table_name) = 'user_account'")
                .forEach(r -> result.add(lower(r.get("t")) + "." + lower(r.get("c"))));
        return result;
    }

    /**
     * Every column that references a user: foreign keys to {@code user_account} plus UUID columns named like a user
     * reference ({@link #USER_COLUMN}), without the audit columns and the {@code owner_id} of translation tables.
     */
    public static Set<String> userReferences(JdbcTemplate jdbc) {
        Set<String> result = foreignKeysToUser(jdbc);
        jdbc.queryForList("select table_name t, column_name c from information_schema.columns "
                        + "where lower(table_schema) = 'public' and lower(data_type) = 'uuid'")
                .forEach(r -> {
                    String column = lower(r.get("c"));
                    String table = lower(r.get("t"));
                    if (!AUDIT_COLUMNS.contains(column) && !table.startsWith("databasechangelog")
                            && !table.endsWith("_i18n")
                            && USER_COLUMN.matcher(column).matches()) {
                        result.add(table + "." + column);
                    }
                });
        return result;
    }

    /** Single-column primary key of a table, or null. */
    public static String primaryKey(JdbcTemplate jdbc, String table) {
        List<String> columns = jdbc.queryForList("select kcu.column_name from information_schema.table_constraints tc "
                + "join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name "
                + "and kcu.constraint_schema = tc.constraint_schema "
                + "where tc.constraint_type = 'PRIMARY KEY' and lower(tc.table_name) = ?", String.class, table);
        return columns.size() == 1 ? lower(columns.getFirst()) : null;
    }

    private static String lower(Object value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
