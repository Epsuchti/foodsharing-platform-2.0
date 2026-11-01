package network.foodsharing.search.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.search.domain.SearchKind;
import network.foodsharing.user.domain.UserContacts;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * The {@code search_index} table (A45) and the read-only source queries that feed it (decision 77: search is a read
 * model over the tables of user, region, store, forum, fsp, event and poll; it never writes them).
 *
 * <p>Two SQL paths, one ordering (decision 147): on PostgreSQL a term matches the full-text vector
 * ({@code fs_german}: unaccent + german stemming, prefix), the trigram word similarity ({@code <%}, also for the
 * ae/oe/ue folded spelling) or the plain infix; hits are ranked by {@code ts_rank} + similarity. On H2 (local, e2e,
 * tests) a term is a plain {@code LIKE '%term%'} on the Java-normalised {@code search_text}, relevance 0. Both paths
 * share the visibility filter and the own-rank ordering: own-rank, relevance desc, title, id (decision 138).
 */
@Repository
public class SearchIndexRepository {

    /**
     * One indexable entity; {@code indexable=false} removes it from the index (deleted, inactive, cancelled).
     * {@code addressText} (stores) is matched only for queries with two or more terms.
     */
    public record Source(SearchKind kind, UUID entityId, String title, String subtitle, String extraText,
                         String addressText, UUID regionId, String visibility, String link, String imageUrl,
                         boolean indexable) {
    }

    /**
     * Who searches: {@code unrestricted} (ORGA) skips the visibility filter; otherwise PUBLIC rows, REGION_MEMBERS /
     * FORUM rows of {@code memberRegionIds}, ADMINS rows of {@code adminRegionIds} and, for users, the contacts of
     * {@code userId}.
     */
    public record Scope(UUID userId, boolean unrestricted, Collection<UUID> memberRegionIds,
                        Collection<UUID> adminRegionIds) {
    }

    /** A thread found through one of its posts (SEARCH_FORUM_POSTS), with the matching post's body. */
    public record PostHit(SearchHitRow thread, String body) {
    }

    /**
     * Word similarity threshold of {@code <%} on PostgreSQL (default 0.6): 0.5 lets one-letter typos of short words
     * match ("zuerch" → "zurich"). Set per transaction, so callers search inside one (SearchService does).
     */
    static final String WORD_SIMILARITY_THRESHOLD = "0.5";

    /** Sorts after every real event / poll distance (seconds). */
    private static final double NO_DISTANCE = 1.0e12;

    private final NamedParameterJdbcTemplate jdbc;
    private volatile Boolean postgres;

    public SearchIndexRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** PostgreSQL (full-text path) or not (H2 LIKE path); read once from the connection metadata. */
    public boolean isPostgres() {
        Boolean value = postgres;
        if (value == null) {
            value = Boolean.TRUE.equals(jdbc.getJdbcTemplate().execute((ConnectionCallback<Boolean>) connection ->
                    connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres")));
            postgres = value;
        }
        return value;
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
                            null, rs.getString(4), null, rs.getObject(5, UUID.class), "REGION_MEMBERS",
                            "/profile/" + id, rs.getString(6), rs.getBoolean(7) && rs.getTimestamp(8) == null));
                });
        jdbc.query("select t.id, t.name, t.type, t.active, p.name from region t left join region p on p.id = t.parent_id"
                + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    boolean group = "WORKING_GROUP".equals(rs.getString(3));
                    out.add(new Source(group ? SearchKind.GROUP : SearchKind.REGION, id, rs.getString(2),
                            rs.getString(5), null, null, id, "PUBLIC", (group ? "/groups/" : "/regions/") + id, null,
                            rs.getBoolean(4)));
                });
        jdbc.query("select t.id, t.name, t.city, t.street, t.postcode, t.region_id, t.deleted_at from store t" + changed,
                p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    String address = String.join(" ", nz(rs.getString(4)), nz(rs.getString(5)), nz(rs.getString(3)));
                    out.add(new Source(SearchKind.STORE, id, rs.getString(2), rs.getString(3), null, address.trim(),
                            rs.getObject(6, UUID.class), "REGION_MEMBERS", "/stores/" + id, null,
                            rs.getTimestamp(7) == null));
                });
        jdbc.query("select t.id, t.title, t.region_id, t.ambassador_forum, t.deleted_at, t.active, r.name"
                + " from forum_thread t join region r on r.id = t.region_id" + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.add(new Source(SearchKind.THREAD, id, rs.getString(2), rs.getString(7), null, null,
                            rs.getObject(3, UUID.class), rs.getBoolean(4) ? "ADMINS" : "FORUM", "/forum/threads/" + id,
                            null, rs.getTimestamp(5) == null && rs.getBoolean(6)));
                });
        jdbc.query("select t.id, t.name, t.city, t.street, t.region_id, t.status, t.deleted_at from food_share_point t"
                + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.add(new Source(SearchKind.FSP, id, rs.getString(2), rs.getString(3), rs.getString(4), null,
                            rs.getObject(5, UUID.class), "PUBLIC", "/food-share-points/" + id, null,
                            "ACTIVE".equals(rs.getString(6)) && rs.getTimestamp(7) == null));
                });
        jdbc.query("select t.id, t.name, t.region_id, t.is_public, t.starts_at, t.deleted_at from event t" + changed, p,
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    Timestamp starts = rs.getTimestamp(5);
                    out.add(new Source(SearchKind.EVENT, id, rs.getString(2),
                            starts == null ? null : starts.toInstant().toString().substring(0, 10), null, null,
                            rs.getObject(3, UUID.class), rs.getBoolean(4) ? "PUBLIC" : "REGION_MEMBERS",
                            "/events/" + id, null, rs.getTimestamp(6) == null));
                });
        jdbc.query("select t.id, t.name, t.region_id, t.cancelled_at, r.name from poll t join region r on r.id = t.region_id"
                + changed, p, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.add(new Source(SearchKind.POLL, id, rs.getString(2), rs.getString(5), null, null,
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

    /**
     * Replaces the rows of the given sources (delete + insert keeps it portable). {@code texts} / {@code addresses}
     * are the normalised search and address texts; PostgreSQL also fills {@code search_vector}.
     */
    public void upsert(Collection<Source> sources, Map<Source, String> texts, Map<Source, String> addresses,
                       Instant now) {
        if (sources.isEmpty()) {
            return;
        }
        List<SqlParameterSource> params = new ArrayList<>();
        for (Source s : sources) {
            params.add(new MapSqlParameterSource("id", UUID.randomUUID()).addValue("now", Timestamp.from(now))
                    .addValue("kind", s.kind().name()).addValue("entity", s.entityId())
                    .addValue("title", cut(s.title(), 300)).addValue("subtitle", cut(s.subtitle(), 300))
                    .addValue("text", cut(texts.get(s), 4000)).addValue("address", cut(addresses.get(s), 500))
                    .addValue("region", s.regionId()).addValue("visibility", s.visibility()).addValue("link", s.link())
                    .addValue("image", cut(s.imageUrl(), 500)));
        }
        SqlParameterSource[] batch = params.toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("delete from search_index where kind = :kind and entity_id = :entity", batch);
        String vectorColumn = isPostgres() ? ", search_vector" : "";
        String vectorValue = isPostgres() ? ", to_tsvector('fs_german', :text)" : "";
        jdbc.batchUpdate("insert into search_index (id, version, created_at, updated_at, kind, entity_id, title,"
                + " subtitle, search_text, address_text, region_id, visibility, link, image_url" + vectorColumn
                + ") values (:id, 0, :now, :now, :kind, :entity, :title, :subtitle, :text, :address, :region,"
                + " :visibility, :link, :image" + vectorValue + ")", batch);
    }

    /**
     * Rows written before changelog 179 (no address on stores, on PostgreSQL no vector): the indexer rebuilds once.
     */
    public boolean needsRebuild() {
        String vector = isPostgres() ? " or search_vector is null" : "";
        Integer found = jdbc.queryForObject("select count(*) from (select 1 from search_index where (kind = 'STORE'"
                + " and address_text is null)" + vector + " limit 1) x", new MapSqlParameterSource(), Integer.class);
        return found != null && found > 0;
    }

    /**
     * Hits of one kind matching every term (see the class comment for the two paths), filtered by {@code scope},
     * ordered by own-rank (REGION/GROUP: admin, member, other; STORE: manager, team, other; USER: buddy, other;
     * EVENT/POLL: distance of {@code starts_at} to {@code now}), relevance, title, id.
     */
    public List<SearchHitRow> search(SearchKind kind, List<String> terms, Scope scope, Instant now, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("kind", kind.name()).addValue("limit", limit)
                .addValue("user", scope.userId()).addValue("nowEpoch", now.getEpochSecond());
        boolean pg = isPostgres();
        boolean address = kind == SearchKind.STORE && terms.size() >= 2;
        StringBuilder where = new StringBuilder(" where si.kind = :kind");
        List<String> similarity = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            p.addValue("l" + i, "%" + escape(term) + "%");
            where.append(" and (si.search_text like :l").append(i);
            if (address) {
                where.append(" or si.address_text like :l").append(i);
            }
            if (pg) {
                p.addValue("t" + i, term);
                where.append(" or :t").append(i).append(" <% si.search_text");
                String folded = fold(term);
                if (!folded.equals(term)) {
                    p.addValue("f" + i, folded);
                    p.addValue("lf" + i, "%" + escape(folded) + "%");
                    where.append(" or :f").append(i).append(" <% si.search_text or si.search_text like :lf").append(i);
                }
                String query = tsQuery(term);
                if (!query.isEmpty()) {
                    p.addValue("q" + i, query);
                    queries.add(query);
                    where.append(" or si.search_vector @@ to_tsquery('fs_german', :q").append(i).append(')');
                }
                if (address) {
                    where.append(" or :t").append(i).append(" <% si.address_text");
                }
                similarity.add("word_similarity(:t" + i + ", si.search_text)");
            }
            where.append(')');
        }
        where.append(visibility(kind, scope, p));
        String relevance = "0";
        if (pg && !similarity.isEmpty()) {
            relevance = "(" + String.join(" + ", similarity) + ") / " + similarity.size();
            if (!queries.isEmpty()) {
                p.addValue("qall", String.join(" & ", queries));
                relevance = "ts_rank(si.search_vector, to_tsquery('fs_german', :qall)) + " + relevance;
            }
        }
        if (pg) {
            jdbc.queryForObject("select set_config('pg_trgm.word_similarity_threshold', :threshold, true)",
                    new MapSqlParameterSource("threshold", WORD_SIMILARITY_THRESHOLD), String.class);
        }
        String sql = "select si.kind, si.entity_id, si.title, si.subtitle, si.link, si.image_url, " + ownRank(kind)
                + " as own_rank, " + relevance + " as relevance from search_index si" + where
                + " order by own_rank, relevance desc, si.title, si.entity_id limit :limit";
        return jdbc.query(sql, p, (rs, n) -> new SearchHitRow(SearchKind.valueOf(rs.getString(1)),
                rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
    }

    /**
     * Users with exactly this email (case-insensitive, decision 147). {@code memberOfRegionIds} null = all users
     * (ORGA), otherwise only active members of those regions.
     */
    public List<SearchHitRow> usersByEmail(String email, Collection<UUID> memberOfRegionIds, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("email", email.strip().toLowerCase(Locale.ROOT))
                .addValue("limit", limit);
        String scope = "";
        if (memberOfRegionIds != null) {
            if (memberOfRegionIds.isEmpty()) {
                return List.of();
            }
            scope = " and exists (select 1 from region_membership m where m.user_id = u.id and m.active = true"
                    + " and m.region_id in (:regions))";
            p.addValue("regions", memberOfRegionIds);
        }
        return jdbc.query("select si.kind, si.entity_id, si.title, si.subtitle, si.link, si.image_url"
                        + " from search_index si join user_account u on u.id = si.entity_id where si.kind = 'USER'"
                        + " and lower(u.email) = :email" + scope + " order by si.title, si.entity_id limit :limit", p,
                (rs, n) -> new SearchHitRow(SearchKind.valueOf(rs.getString(1)), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
    }

    /**
     * Threads whose visible posts (not hidden, not deleted) contain every term (SEARCH_FORUM_POSTS, decision 147),
     * newest post first; only threads in the index (active, not deleted) and visible to {@code scope} like their THREAD
     * rows. One row per matching post, the caller keeps the first per thread. PostgreSQL: full-text over the body
     * ({@code ix_forum_post_body_fts}); H2: LIKE per term on the lower-cased body.
     */
    public List<PostHit> threadsByPostContent(List<String> terms, Scope scope, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit);
        StringBuilder where = new StringBuilder(" where p.hidden_at is null and p.deleted_at is null");
        if (isPostgres()) {
            List<String> queries = terms.stream().map(SearchIndexRepository::tsQuery).filter(q -> !q.isEmpty()).toList();
            if (queries.isEmpty()) {
                return List.of();
            }
            p.addValue("qall", String.join(" & ", queries));
            where.append(" and to_tsvector('fs_german'::regconfig, p.body) @@ to_tsquery('fs_german', :qall)");
        } else {
            for (int i = 0; i < terms.size(); i++) {
                p.addValue("l" + i, "%" + escape(terms.get(i)) + "%");
                where.append(" and lower(p.body) like :l").append(i);
            }
        }
        where.append(visibility(SearchKind.THREAD, scope, p));
        return jdbc.query("select si.entity_id, si.title, si.subtitle, si.link, p.body from forum_post p"
                        + " join search_index si on si.kind = 'THREAD' and si.entity_id = p.thread_id" + where
                        + " order by p.created_at desc, p.id limit :limit", p,
                (rs, n) -> new PostHit(new SearchHitRow(SearchKind.THREAD, rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3), rs.getString(4), null), rs.getString(5)));
    }

    public long count() {
        Long value = jdbc.queryForObject("select count(*) from search_index", new MapSqlParameterSource(), Long.class);
        return value == null ? 0 : value;
    }

    /** Stores the user is in the team of (member or jumper). */
    public List<SearchHitRow> ownStores(UUID userId) {
        return jdbc.query("select s.id, s.name, s.city from store s join store_membership m on m.store_id = s.id"
                        + " where m.user_id = :user and m.status in ('MEMBER', 'JUMPER') and s.deleted_at is null"
                        + " order by s.name, s.id", new MapSqlParameterSource("user", userId),
                (rs, n) -> new SearchHitRow(SearchKind.STORE, rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        "/stores/" + rs.getObject(1, UUID.class), null));
    }

    /** Food share points the user follows or manages. */
    public List<SearchHitRow> ownFoodSharePoints(UUID userId) {
        return jdbc.query("select f.id, f.name, f.city from food_share_point f join fsp_follower o on o.fsp_id = f.id"
                        + " where o.user_id = :user and f.deleted_at is null order by f.name, f.id",
                new MapSqlParameterSource("user", userId),
                (rs, n) -> new SearchHitRow(SearchKind.FSP, rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        "/food-share-points/" + rs.getObject(1, UUID.class), null));
    }

    /** Named conversations of the user (titles only; read model, no dependency on messaging, A33). */
    public List<SearchHitRow> ownConversations(UUID userId, int limit) {
        return jdbc.query("select c.id, c.name from conversation c join conversation_member m on m.conversation_id = c.id"
                        + " where m.user_id = :user and c.name is not null order by c.last_message_at desc, c.id desc limit :limit",
                new MapSqlParameterSource("user", userId).addValue("limit", limit),
                (rs, n) -> new SearchHitRow(null, rs.getObject(1, UUID.class), rs.getString(2), null,
                        "/messages/" + rs.getObject(1, UUID.class), null));
    }

    /** The visibility filter of {@link #search} (users: contacts instead of REGION_MEMBERS). */
    private static String visibility(SearchKind kind, Scope scope, MapSqlParameterSource p) {
        if (scope.unrestricted()) {
            return "";
        }
        if (kind == SearchKind.USER) {
            p.addValue("contactOf", scope.userId());
            return " and si.entity_id in (select u.id from user_account u where " + UserContacts.CONDITION + ")";
        }
        StringBuilder sql = new StringBuilder(" and (si.visibility = 'PUBLIC'");
        if (!scope.memberRegionIds().isEmpty()) {
            sql.append(" or (si.visibility in ('REGION_MEMBERS', 'FORUM') and si.region_id in (:members))");
            p.addValue("members", scope.memberRegionIds());
        }
        if (!scope.adminRegionIds().isEmpty()) {
            sql.append(" or (si.visibility = 'ADMINS' and si.region_id in (:admins))");
            p.addValue("admins", scope.adminRegionIds());
        }
        return sql.append(')').toString();
    }

    /** The own-rank expression of a kind (lower = first); plain SQL for both paths. */
    static String ownRank(SearchKind kind) {
        return switch (kind) {
            case REGION, GROUP -> "case when exists (select 1 from region_admin a where a.region_id = si.entity_id"
                    + " and a.user_id = :user) then 0 when exists (select 1 from region_membership m"
                    + " where m.region_id = si.entity_id and m.user_id = :user and m.active = true) then 1 else 2 end";
            case STORE -> "case when exists (select 1 from store_membership m where m.store_id = si.entity_id"
                    + " and m.user_id = :user and m.manager = true and m.status in ('MEMBER', 'JUMPER')) then 0"
                    + " when exists (select 1 from store_membership m where m.store_id = si.entity_id"
                    + " and m.user_id = :user and m.status in ('MEMBER', 'JUMPER')) then 1 else 2 end";
            case USER -> "case when exists (select 1 from buddy b where b.status = 'CONFIRMED' and ((b.user_id = :user"
                    + " and b.buddy_id = si.entity_id) or (b.buddy_id = :user and b.user_id = si.entity_id))) then 0"
                    + " else 1 end";
            case EVENT -> distance("event");
            case POLL -> distance("poll");
            default -> "0";
        };
    }

    private static String distance(String table) {
        return "coalesce((select abs(extract(epoch from e.starts_at) - :nowEpoch) from " + table
                + " e where e.id = si.entity_id), " + NO_DISTANCE + ")";
    }

    /** PostgreSQL prefix query of one term: its words as {@code word:*}, joined with {@code &} (empty: none). */
    static String tsQuery(String term) {
        String words = term.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        if (words.isEmpty()) {
            return "";
        }
        return String.join(" & ", java.util.Arrays.stream(words.split(" ")).map(w -> w + ":*").toList());
    }

    /** Transliterated umlauts folded (ae → a, oe → o, ue → u): "zuerich" also finds "zurich" by trigrams. */
    public static String fold(String term) {
        return term.replace("ae", "a").replace("oe", "o").replace("ue", "u");
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
