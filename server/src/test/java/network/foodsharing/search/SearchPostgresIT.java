package network.foodsharing.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.search.domain.SearchKind;
import network.foodsharing.search.repository.SearchIndexRepository;
import network.foodsharing.search.repository.SearchIndexRepository.Scope;
import network.foodsharing.search.repository.SearchIndexRepository.Source;
import network.foodsharing.search.service.SearchIndexer;
import network.foodsharing.search.service.SearchService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Decision 147 on PostgreSQL (the full-text path): stemming, umlauts, typos, own-rank order, event distance, id
 * tiebreak, store address only with two or more words, permission filter and forum post contents. Each test runs in
 * a transaction like {@code SearchService.search} (the trigram threshold is set per transaction) and is rolled back.
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema,seed", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
@Transactional
class SearchPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final UUID ZUERICH = UUID.fromString("00000010-0001-4000-8000-000000000040");

    @Autowired
    private SearchIndexRepository index;

    @Autowired
    private SearchIndexer indexer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void buildIndex() {
        indexer.rebuild();
    }

    private static Scope orga(UUID userId) {
        return new Scope(userId, true, Set.of(), Set.of());
    }

    private List<UUID> ids(SearchKind kind, String query, Scope scope) {
        return index.search(kind, SearchService.terms(query, 2), scope, clock.instant(), 20).stream()
                .map(SearchHitRow::entityId).toList();
    }

    private void put(Source... sources) {
        Map<Source, String> texts = new HashMap<>();
        Map<Source, String> addresses = new HashMap<>();
        for (Source s : sources) {
            texts.put(s, SearchIndexer.textOf(s));
            if (s.addressText() != null) {
                addresses.put(s, SearchIndexer.normalize(s.addressText()));
            }
        }
        index.upsert(List.of(sources), texts, addresses, clock.instant());
    }

    private static Source row(SearchKind kind, UUID id, String title, String visibility) {
        return new Source(kind, id, title, null, null, null, null, visibility, "/x/" + id, null, true);
    }

    @Test
    void theIndexCarriesTheVectorAndThePathIsPostgres() {
        assertThat(index.isPostgres()).isTrue();
        assertThat(index.needsRebuild()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from search_index where search_vector is null",
                Integer.class)).isZero();
    }

    @Test
    void stemmingUmlautsAndTypos() {
        UUID plural = UUID.fromString("00000000-0000-4000-8000-00000000a001");
        UUID umlaut = UUID.fromString("00000000-0000-4000-8000-00000000a002");
        put(row(SearchKind.GROUP, plural, "Baeckereien Nord", "PUBLIC"),
                row(SearchKind.GROUP, umlaut, "Bäckerei Süd", "PUBLIC"));
        Scope scope = orga(SeedUsers.ORGA.id());

        assertThat(ids(SearchKind.GROUP, "Bäckerei", scope)).contains(plural, umlaut);
        assertThat(ids(SearchKind.GROUP, "bäckereien", scope)).contains(umlaut);
        // typo and transliterated umlaut
        assertThat(ids(SearchKind.REGION, "Zuerch", scope)).contains(ZUERICH);
        assertThat(ids(SearchKind.REGION, "Zuerich", scope)).contains(ZUERICH);
    }

    @Test
    void regionsOrderedAdminThenMemberThenOthers() {
        Map<String, Object> admin = jdbc.queryForMap("select a.user_id, a.region_id from region_admin a"
                + " join region r on r.id = a.region_id where r.type <> 'WORKING_GROUP' order by a.id limit 1");
        UUID user = (UUID) admin.get("user_id");
        UUID adminRegion = (UUID) admin.get("region_id");
        UUID memberRegion = jdbc.queryForObject("select m.region_id from region_membership m join region r"
                + " on r.id = m.region_id where m.user_id = ? and m.active = true and r.type <> 'WORKING_GROUP'"
                + " and not exists (select 1 from region_admin a where a.user_id = m.user_id"
                + " and a.region_id = m.region_id) order by m.region_id limit 1", UUID.class, user);
        UUID otherRegion = jdbc.queryForObject("select r.id from region r where r.type <> 'WORKING_GROUP'"
                + " and not exists (select 1 from region_membership m where m.user_id = ? and m.region_id = r.id)"
                + " and not exists (select 1 from region_admin a where a.user_id = ? and a.region_id = r.id)"
                + " order by r.id limit 1", UUID.class, user, user);
        put(row(SearchKind.REGION, otherRegion, "Quokkastadt A", "PUBLIC"),
                row(SearchKind.REGION, memberRegion, "Quokkastadt B", "PUBLIC"),
                row(SearchKind.REGION, adminRegion, "Quokkastadt C", "PUBLIC"));

        assertThat(ids(SearchKind.REGION, "quokkastadt", new Scope(user, false, Set.of(), Set.of())))
                .containsExactly(adminRegion, memberRegion, otherRegion);
    }

    @Test
    void storesOrderedManagerThenTeamThenOthersAndAddressOnlyWithTwoWords() {
        Map<String, Object> manager = jdbc.queryForMap("select m.user_id, m.store_id from store_membership m"
                + " where m.manager = true and m.status = 'MEMBER' and exists (select 1 from store_membership t"
                + " where t.user_id = m.user_id and t.manager = false and t.status in ('MEMBER', 'JUMPER'))"
                + " order by m.id limit 1");
        UUID user = (UUID) manager.get("user_id");
        UUID managed = (UUID) manager.get("store_id");
        UUID team = jdbc.queryForObject("select store_id from store_membership where user_id = ? and manager = false"
                + " and status in ('MEMBER', 'JUMPER') order by store_id limit 1", UUID.class, user);
        UUID other = jdbc.queryForObject("select s.id from store s where not exists (select 1 from store_membership m"
                + " where m.store_id = s.id and m.user_id = ?) order by s.id limit 1", UUID.class, user);
        put(new Source(SearchKind.STORE, other, "Quokkaladen A", "Zürich", null, "Bahnhofstrasse 1 8001 Zürich", null,
                        "REGION_MEMBERS", "/stores/" + other, null, true),
                new Source(SearchKind.STORE, team, "Quokkaladen B", "Bern", null, "Marktgasse 2 3011 Bern", null,
                        "REGION_MEMBERS", "/stores/" + team, null, true),
                new Source(SearchKind.STORE, managed, "Quokkaladen C", "Basel", null, "Freie Strasse 3 4001 Basel",
                        null, "REGION_MEMBERS", "/stores/" + managed, null, true));
        Scope scope = orga(user);

        assertThat(ids(SearchKind.STORE, "quokkaladen", scope)).containsExactly(managed, team, other);
        assertThat(ids(SearchKind.STORE, "Bahnhofstrasse", scope)).doesNotContain(other);
        assertThat(ids(SearchKind.STORE, "Marktgasse", scope)).doesNotContain(team);
        assertThat(ids(SearchKind.STORE, "Quokkaladen Bahnhofstrasse", scope)).containsExactly(other);
        assertThat(ids(SearchKind.STORE, "Marktgasse 3011", scope)).contains(team).doesNotContain(other, managed);
    }

    @Test
    void buddiesFirstAndNonContactsHidden() {
        UUID foodsaver = SeedUsers.FOODSAVER.id();
        put(row(SearchKind.USER, SeedUsers.JUMPER.id(), "Quokka Aaron", "REGION_MEMBERS"),
                row(SearchKind.USER, SeedUsers.BIEB.id(), "Quokka Zora", "REGION_MEMBERS"),
                row(SearchKind.USER, SeedUsers.NEWBIE.id(), "Quokka Nora", "REGION_MEMBERS"));
        List<UUID> found = ids(SearchKind.USER, "quokka", new Scope(foodsaver, false, Set.of(), Set.of()));

        assertThat(found).startsWith(SeedUsers.BIEB.id()).contains(SeedUsers.JUMPER.id())
                .doesNotContain(SeedUsers.NEWBIE.id());
        assertThat(ids(SearchKind.USER, "quokka", orga(SeedUsers.ORGA.id()))).contains(SeedUsers.NEWBIE.id());
    }

    @Test
    void eventsByDistanceToNowAndIdTiebreak() {
        List<Map<String, Object>> events = jdbc.queryForList("select id, starts_at from event where deleted_at is null"
                + " order by id limit 3");
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        Instant now = clock.instant();
        Source[] rows = events.stream().map(e -> row(SearchKind.EVENT, (UUID) e.get("id"), "Quokkafest", "PUBLIC"))
                .toArray(Source[]::new);
        put(rows);
        List<UUID> expected = events.stream().sorted(Comparator.comparingLong((Map<String, Object> e) ->
                        Math.abs(((Timestamp) e.get("starts_at")).toInstant().getEpochSecond() - now.getEpochSecond()))
                .thenComparing(e -> e.get("id").toString())).map(e -> (UUID) e.get("id")).toList();
        assertThat(ids(SearchKind.EVENT, "quokkafest", orga(SeedUsers.ORGA.id()))).containsExactlyElementsOf(expected);

        UUID first = UUID.fromString("00000000-0000-4000-8000-00000000b001");
        UUID second = UUID.fromString("00000000-0000-4000-8000-00000000b002");
        put(row(SearchKind.GROUP, second, "Quokkagruppe", "PUBLIC"), row(SearchKind.GROUP, first, "Quokkagruppe", "PUBLIC"));
        assertThat(ids(SearchKind.GROUP, "quokkagruppe", orga(SeedUsers.ORGA.id()))).containsExactly(first, second);
    }

    @Test
    void permissionFilter() {
        UUID store = jdbc.queryForObject("select id from store order by id limit 1", UUID.class);
        UUID region = jdbc.queryForObject("select region_id from store where id = ?", UUID.class, store);
        put(new Source(SearchKind.STORE, store, "Quokkamarkt", null, null, "", region, "REGION_MEMBERS",
                "/stores/" + store, null, true));

        assertThat(ids(SearchKind.STORE, "quokkamarkt", new Scope(SeedUsers.NEWBIE.id(), false, Set.of(), Set.of())))
                .isEmpty();
        assertThat(ids(SearchKind.STORE, "quokkamarkt", new Scope(SeedUsers.NEWBIE.id(), false, Set.of(region),
                Set.of()))).containsExactly(store);
    }

    @Test
    void forumPostsByContentWithoutHiddenOnes() {
        Map<String, Object> post = jdbc.queryForMap("select p.id, p.thread_id, t.region_id from forum_post p"
                + " join forum_thread t on t.id = p.thread_id join search_index si on si.kind = 'THREAD'"
                + " and si.entity_id = t.id where p.hidden_at is null and p.deleted_at is null"
                + " and t.ambassador_forum = false order by p.id limit 1");
        jdbc.update("update forum_post set body = 'Der Kühlschrank der Quokkabäckereien ist repariert.' where id = ?",
                post.get("id"));
        UUID region = (UUID) post.get("region_id");
        Scope member = new Scope(SeedUsers.FOODSAVER.id(), false, Set.of(region), Set.of());

        assertThat(index.threadsByPostContent(SearchService.terms("kuhlschrank quokkabackerei", 2), member, 20))
                .extracting(hit -> hit.thread().entityId()).contains((UUID) post.get("thread_id"));
        assertThat(index.threadsByPostContent(SearchService.terms("quokkabackerei", 2),
                new Scope(SeedUsers.NEWBIE.id(), false, Set.of(), Set.of()), 20)).isEmpty();

        jdbc.update("update forum_post set hidden_at = ? where id = ?", Timestamp.from(clock.instant()), post.get("id"));
        assertThat(index.threadsByPostContent(SearchService.terms("quokkabackerei", 2), member, 20)).isEmpty();
    }
}
