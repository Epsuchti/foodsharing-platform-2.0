package network.foodsharing.statistics.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate queries over the tables of pickup, store, user, fsp, basket and forum (decision 77: the
 * statistics module is a reporting read model; it never writes foreign tables). Plain SQL that runs on PostgreSQL and
 * H2 ({@code MODE=PostgreSQL}). A pickup = one confirmed past pickup sign-up; its kg = the average of the store's
 * weight category (upstream).
 */
@Repository
public class StatisticsReadModel {

    public record Totals(double kgSaved, long pickups, long foodsavers, long stores, long cooperations,
                         long foodSharePoints, long baskets) {
    }

    public record UserFigures(int pickups, double kg, int posts, int baskets) {
        public static final UserFigures NONE = new UserFigures(0, 0, 0, 0);
    }

    public record PickupRow(Instant pickupAt, double kg) {
    }

    private static final String PICKUPS = " from pickup_signup ps join store s on s.id = ps.store_id"
            + " left join pickup_weight_category w on w.code = s.weight_category"
            + " where ps.confirmed = true and ps.pickup_at < :before";

    private final NamedParameterJdbcTemplate jdbc;

    public StatisticsReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(Instant before) {
        MapSqlParameterSource params = new MapSqlParameterSource("before", Timestamp.from(before));
        Object[] pickups = jdbc.queryForObject("select count(*), coalesce(sum(w.average_kg), 0)" + PICKUPS, params,
                (rs, n) -> new Object[] {rs.getLong(1), rs.getDouble(2)});
        long foodsavers = count("select count(*) from user_account where active = true and verified = true"
                + " and deleted_at is null and role <> 'FOODSHARER'");
        long stores = count("select count(*) from store where deleted_at is null");
        long cooperations = count("select count(*) from store where deleted_at is null"
                + " and cooperation_status = 'COOPERATION_ESTABLISHED'");
        long fsps = count("select count(*) from food_share_point where deleted_at is null and status = 'ACTIVE'");
        long baskets = count("select count(*) from basket");
        return new Totals(Math.round((double) pickups[1] * 10) / 10.0, (long) pickups[0], foodsavers, stores,
                cooperations, fsps, baskets);
    }

    /** Pickups, kg, forum posts and shared baskets per user (users without any are absent). */
    public Map<UUID, UserFigures> userFigures(Instant before) {
        Map<UUID, UserFigures> figures = new HashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("before", Timestamp.from(before));
        jdbc.query("select ps.user_id, count(*), coalesce(sum(w.average_kg), 0)" + PICKUPS + " group by ps.user_id",
                params, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    figures.put(id, new UserFigures(rs.getInt(2), Math.round(rs.getDouble(3) * 10) / 10.0, 0, 0));
                });
        jdbc.query("select author_id, count(*) from forum_post where deleted_at is null group by author_id",
                new MapSqlParameterSource(), rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    UserFigures old = figures.getOrDefault(id, UserFigures.NONE);
                    figures.put(id, new UserFigures(old.pickups(), old.kg(), rs.getInt(2), old.baskets()));
                });
        jdbc.query("select owner_id, count(*) from basket group by owner_id", new MapSqlParameterSource(), rs -> {
            UUID id = rs.getObject(1, UUID.class);
            UserFigures old = figures.getOrDefault(id, UserFigures.NONE);
            figures.put(id, new UserFigures(old.pickups(), old.kg(), old.posts(), rs.getInt(2)));
        });
        return figures;
    }

    /** Confirmed pickups of live stores in the regions within [from, before). */
    public List<PickupRow> pickupsInRegions(Collection<UUID> regionIds, Instant from, Instant before) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("before", Timestamp.from(before))
                .addValue("from", Timestamp.from(from)).addValue("regions", regionIds);
        return jdbc.query("select ps.pickup_at, coalesce(w.average_kg, 0)" + PICKUPS
                        + " and ps.pickup_at >= :from and s.deleted_at is null and s.region_id in (:regions)", params,
                (rs, n) -> new PickupRow(rs.getTimestamp(1).toInstant(), rs.getDouble(2)));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return value == null ? 0 : value;
    }
}
