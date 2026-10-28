package network.foodsharing.common.persistence;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for the account deletion / data export contributors (A40): bulk-delete or read the rows of one JPA entity
 * that belong to a user ({@code <alias>.<userField> = :userId}). Runs in the caller's transaction.
 */
public final class UserRows {

    private UserRows() {
    }

    /** Deletes every {@code entity} row whose {@code userField} is the user; returns the count. */
    public static int delete(EntityManager em, String entity, String userField, UUID userId) {
        return em.createQuery("delete from " + entity + " e where e." + userField + " = :userId")
                .setParameter("userId", userId).executeUpdate();
    }

    /** The named fields of the user's {@code entity} rows, one map per row (export). */
    public static List<Map<String, Object>> read(EntityManager em, String entity, String userField, UUID userId,
                                                 String... fields) {
        StringBuilder select = new StringBuilder("select ");
        for (int i = 0; i < fields.length; i++) {
            select.append(i == 0 ? "" : ", ").append("e.").append(fields[i]);
        }
        select.append(" from ").append(entity).append(" e where e.").append(userField).append(" = :userId");
        List<?> rows = em.createQuery(select.toString()).setParameter("userId", userId).getResultList();
        return rows.stream().map(row -> {
            Object[] values = fields.length == 1 ? new Object[] {row} : (Object[]) row;
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < fields.length; i++) {
                map.put(fields[i], values[i]);
            }
            return map;
        }).toList();
    }
}
