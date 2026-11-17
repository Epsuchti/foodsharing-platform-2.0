package network.foodsharing.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Changelog 198 on H2: production (context {@code schema} only) gets the EINAB_GUIDE quiz's name in all five
 * languages that changelog 190 left out; the seed loads on top without conflicts, and a run on a database that already
 * has texts only adds the missing languages and keeps edited ones. PostgreSQL runs the same SQL in
 * StagingSeedOnExistingSchemaPostgresIT (schema first, seed on top).
 */
class GuideQuizTextsMigrationIT {

    private static final String MASTER = "classpath:db/changelog/db.changelog-master.yaml";
    private static final String LANGUAGES = "de,en,fr,gws,it";
    private static final String TEXTS = "select string_agg(i.language, ',' order by i.language) from quiz_i18n i "
            + "join quiz q on q.id = i.owner_id where q.kind = 'EINAB_GUIDE'";

    @Test
    void productionGetsTheGuideQuizNameInFiveLanguagesAndTheSeedLoadsOnTop() throws Exception {
        DriverManagerDataSource dataSource = dataSource("guide-quiz-198-");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        run(dataSource, "schema");
        assertThat(jdbc.queryForObject(TEXTS, String.class)).isEqualTo(LANGUAGES);
        assertThat(jdbc.queryForObject("select i.name from quiz_i18n i join quiz q on q.id = i.owner_id "
                + "where q.kind = 'EINAB_GUIDE' and i.language = 'de'", String.class))
                .isEqualTo("EinAb-Begleitungs-Quiz");

        run(dataSource, "schema,seed");
        assertThat(jdbc.queryForObject(TEXTS, String.class)).isEqualTo(LANGUAGES);
    }

    @Test
    void addsOnlyMissingLanguagesAndKeepsEditedTexts() throws Exception {
        DriverManagerDataSource dataSource = dataSource("guide-quiz-198-rerun-");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        run(dataSource, "schema");
        // an editor renamed the German quiz under another id; English and Italian are gone
        jdbc.update("delete from quiz_i18n where language in ('de', 'en', 'it') and owner_id = "
                + "(select id from quiz where kind = 'EINAB_GUIDE')");
        jdbc.update("insert into quiz_i18n (id, version, created_at, updated_at, owner_id, language, name) "
                + "select ?, 0, current_timestamp, current_timestamp, id, 'de', 'Begleit-Quiz' from quiz "
                + "where kind = 'EINAB_GUIDE'", UUID.randomUUID());
        assertThat(jdbc.update("delete from databasechangelog where id = '198-1'")).isEqualTo(1);

        run(dataSource, "schema");

        assertThat(jdbc.queryForObject(TEXTS, String.class)).isEqualTo(LANGUAGES);
        assertThat(jdbc.queryForObject("select i.name from quiz_i18n i join quiz q on q.id = i.owner_id "
                + "where q.kind = 'EINAB_GUIDE' and i.language = 'de'", String.class)).isEqualTo("Begleit-Quiz");
    }

    private static DriverManagerDataSource dataSource(String prefix) {
        return new DriverManagerDataSource("jdbc:h2:mem:" + prefix + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void run(DriverManagerDataSource dataSource, String contexts) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(MASTER);
        liquibase.setContexts(contexts);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
    }
}
