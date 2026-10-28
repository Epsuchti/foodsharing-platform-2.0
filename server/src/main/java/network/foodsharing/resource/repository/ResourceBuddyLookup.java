package network.foodsharing.resource.repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Confirmed buddies of a user for the private-resource rule. Read-only query on the profile module's {@code buddy}
 * table (profile is a leaf module nobody may depend on, A38).
 */
@Repository
public class ResourceBuddyLookup {

    private final JdbcTemplate jdbc;

    public ResourceBuddyLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<UUID> confirmedBuddies(UUID userId) {
        Set<UUID> ids = new HashSet<>(jdbc.queryForList(
                "select buddy_id from buddy where user_id = ? and status = 'CONFIRMED'", UUID.class, userId));
        ids.addAll(jdbc.queryForList("select user_id from buddy where buddy_id = ? and status = 'CONFIRMED'",
                UUID.class, userId));
        return ids;
    }
}
