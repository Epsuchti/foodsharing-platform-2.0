package network.foodsharing.search.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.search.domain.SearchKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * The {@code search_index} table (A45) and the read-only source queries that feed it (decision 77: search is a read
 * model over the tables of user, region, store, forum, fsp, event and poll; it never writes them). Plain SQL for
 * PostgreSQL and H2.
 */
@Repository
public class SearchIndexRepository {

    /** One indexable entity; {@code indexable=false} removes it from the index (deleted, inactive, cancelled). */
    public record Source(SearchKind kind, UUID entityId, String title, String subtitle, String extraText,
                         UUID regionId, String visibility, String link, String imageUrl, boolean indexable) {
    }


    private final NamedParameterJdbcTemplate jdbc;

    public SearchIndexRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Source rows changed at or after {@code since} (all when null). */
    public List<Source> sources(Instant since) {
        MapSqlParameterSource p = new MapSqlParameterSource("since", since == null ? null : Timestamp.from(since));
        String changed = since == null ? "" : " where t.updated_at >= :since";
        List<Source> out = new ArrayList<>();
        jdbc.query("select t.id, t.first_name, t.last_name, t.display_name, t.home_region_id, t.avatar_url,"
                + " t.active, t.deleted_at from user_account t" + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    String name = (nz(rs.getString(2)) + " " + nz(rs.getString(3))).trim();
                    out.add(new Source(SearchKind.USER, id, name.isEmpty() ? nz(rs.getString(4)) : name,
                            null, rs.getString(4), rs.getObject(5, UUID.class), "REGION_MEMBERS", "/users/" + id,
                            rs.getString(6), rs.getBoolean(7) && rs.getTimestamp(8) == null));
                });
        jdbc.query("select t.id, t.name, t.type, t.active, p.name from region t left join region p on p.id = t.parent_id"
                + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    boolean group = "WORKING_GROUP".equals(rs.getString(3));
                    out.add(new Source(group ? SearchKind.GROUP : SearchKind.REGION, id, rs.getString(2),
                            rs.getString(5), null, id, "PUBLIC", (group ? "/groups/" : "/regions/") + id, null,
                            rs.getBoolean(4)));
                });
        jdbc.query("select t.id, t.name, t.city, t.street, t.region_id, t.deleted_at from store t" + changed, p, rs -> {
            UUID id = rs.getObject(1, UUID.class);
            out.add(new Source(SearchKind.STORE, id, rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getObject(5, UUID.class), "REGION_MEMBERS", "/stores/" + id, null, rs.getTimestamp(6) == null));
        });
        jdbc.query("select t.id, t.title, t.region_id, t.ambassador_forum, t.deleted_at, t.active, r.name"
                + " from forum_thread t join region r on r.id = t.region_id" + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.add(new Source(SearchKind.THREAD, id, rs.getString(2), rs.getString(7), null,
                            rs.getObject(3, UUID.class), rs.getBoolean(4) ? "ADMINS" : "FORUM", "/forum/threads/" + id,
                            null, rs.getTimestamp(5) == null && rs.getBoolean(6)));
                });
        jdbc.query("select t.id, t.name, t.city, t.street, t.region_id, t.status, t.deleted_at from food_share_point t"
                + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.add(new Source(SearchKind.FSP, id, rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getObject(5, UUID.class), "PUBLIC", "/food-share-points/" + id, null,
                            "ACTIVE".equals(rs.getString(6)) && rs.getTimestamp(7) == null));
                });
        jdbc.query("select t.id, t.name, t.region_id, t.is_public, t.starts_at, t.deleted_at from event t" + changed, p,
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    Timestamp starts = rs.getTimestamp(5);
                    out.add(new Source(SearchKind.EVENT, id, rs.getString(2),
                            starts == null ? null : starts.toInstant().toString().substring(0, 10), null,
                            rs.getObject(3, UUID.class), rs.getBoolean(4) ? "PUBLIC" : "REGION_MEMBERS",
                            "/events/" + id, null, rs.getTimestamp(6) == null));
                });
        jdbc.query("select t.id, t.name, t.region_id, t.cancelled_at, r.name from poll t join region r on r.id = t.region_id"
                + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.add(new Source(SearchKind.POLL, id, rs.getString(2), rs.getString(5), null,
                            rs.getObject(3, UUID.class), "REGION_MEMBERS", "/polls/" + id, null,
                            rs.getTimestamp(4) == null));
                });
        return out;
    }

    public void deleteAll() {
        jdbc.update("delete from search_index", new MapSqlParameterSource());
    }

    public void remove(SearchKind kind, UUID entityId) {
        jdbc.update("delete from search_index where kind = :kind and entity_id = :id",
                new MapSqlParameterSource("kind", kind.name()).addValue("id", entityId));
    }

    /** Replaces the rows of the given sources (delete + insert keeps it portable). */
    public void upsert(Collection<Source> sources, Map<Source, String> texts, Instant now) {
        if (sources.isEmpty()) {
            return;
        }
        List<SqlParameterSource> params = new ArrayList<>();
        for (Source s : sources) {
            params.add(new MapSqlParameterSource("id", UUID.randomUUID()).addValue("now", Timestamp.from(now))
                    .addValue("kind", s.kind().name()).addValue("entity", s.entityId())
                    .addValue("title", cut(s.title(), 300)).addValue("subtitle", cut(s.subtitle(), 300))
                    .addValue("text", cut(texts.get(s), 4000)).addValue("region", s.regionId())
                    .addValue("visibility", s.visibility()).addValue("link", s.link())
                    .addValue("image", cut(s.imageUrl(), 500)));
        }
        SqlParameterSource[] batch = params.toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("delete from search_index where kind = :kind and entity_id = :entity", batch);
        jdbc.batchUpdate("insert into search_index (id, version, created_at, updated_at, kind, entity_id, title,"
                + " subtitle, search_text, region_id, visibility, link, image_url) values (:id, 0, :now, :now, :kind,"
                + " :entity, :title, :subtitle, :text, :region, :visibility, :link, :image)", batch);
    }

    /**
     * Hits of one kind whose text contains every term; {@code unrestricted} (ORGA) skips the visibility filter,
     * otherwise PUBLIC rows, REGION_MEMBERS / FORUM rows of {@code memberRegionIds} and ADMINS rows of
     * {@code adminRegionIds}.
     */
    public List<SearchHitRow> search(SearchKind kind, List<String> terms, boolean unrestricted, Collection<UUID> memberRegionIds,
                            Collection<UUID> adminRegionIds, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("kind", kind.name()).addValue("limit", limit);
        StringBuilder sql = new StringBuilder("select kind, entity_id, title, subtitle, link, image_url"
                + " from search_index where kind = :kind");
        for (int i = 0; i < terms.size(); i++) {
            sql.append(" and search_text like :t").append(i);
            p.addValue("t" + i, "%" + escape(terms.get(i)) + "%");
        }
        if (!unrestricted) {
            sql.append(" and (visibility = 'PUBLIC'");
            if (!memberRegionIds.isEmpty()) {
                sql.append(" or (visibility in ('REGION_MEMBERS', 'FORUM') and region_id in (:members))");
                p.addValue("members", memberRegionIds);
            }
            if (!adminRegionIds.isEmpty()) {
                sql.append(" or (visibility = 'ADMINS' and region_id in (:admins))");
                p.addValue("admins", adminRegionIds);
            }
            sql.append(')');
        }
        sql.append(" order by title limit :limit");
        return jdbc.query(sql.toString(), p, (rs, n) -> new SearchHitRow(SearchKind.valueOf(rs.getString(1)),
                rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
    }

    public long count() {
        Long value = jdbc.queryForObject("select count(*) from search_index", new MapSqlParameterSource(), Long.class);
        return value == null ? 0 : value;
    }

    /** Stores the user is in the team of (member or jumper). */
    public List<SearchHitRow> ownStores(UUID userId) {
        return jdbc.query("select s.id, s.name, s.city from store s join store_membership m on m.store_id = s.id"
                        + " where m.user_id = :user and m.status in ('MEMBER', 'JUMPER') and s.deleted_at is null"
                        + " order by s.name", new MapSqlParameterSource("user", userId),
                (rs, n) -> new SearchHitRow(SearchKind.STORE, rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        "/stores/" + rs.getObject(1, UUID.class), null));
    }

    /** Food share points the user follows or manages. */
    public List<SearchHitRow> ownFoodSharePoints(UUID userId) {
        return jdbc.query("select f.id, f.name, f.city from food_share_point f join fsp_follower o on o.fsp_id = f.id"
                        + " where o.user_id = :user and f.deleted_at is null order by f.name",
                new MapSqlParameterSource("user", userId),
                (rs, n) -> new SearchHitRow(SearchKind.FSP, rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        "/food-share-points/" + rs.getObject(1, UUID.class), null));
    }

    /** Named conversations of the user (titles only; read model, no dependency on messaging, A33). */
    public List<SearchHitRow> ownConversations(UUID userId, int limit) {
        return jdbc.query("select c.id, c.name from conversation c join conversation_member m on m.conversation_id = c.id"
                        + " where m.user_id = :user and c.name is not null order by c.last_message_at desc limit :limit",
                new MapSqlParameterSource("user", userId).addValue("limit", limit),
                (rs, n) -> new SearchHitRow(null, rs.getObject(1, UUID.class), rs.getString(2), null,
                        "/messages/" + rs.getObject(1, UUID.class), null));
    }

    private static String escape(String term) {
        return term.replace("\\", "").replace("%", "").replace("_", "");
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
