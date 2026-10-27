package network.foodsharing.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The auth seed files (002 identities, 003 blocklist) against the column contract agreed with T4 (plan-m1 T3):
 * {@code auth_identity(id, base, user_id, provider, subject, label, last_used_at)} and
 * {@code email_blocklist(id, base, pattern, reason)}. Without the tables the changesets are marked as ran (see
 * LocalProfileSmokeIT); once the tables exist (created here with {@code IF NOT EXISTS}, or by changelog 005) they load.
 */
class AuthSeedContractIT {

    private static final String MASTER = "classpath:db/changelog/db.changelog-master.yaml";

    @Test
    void authSeedsLoadAgainstTheAgreedColumns() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:auth-seed-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        run(dataSource, "schema");
        jdbc.execute("""
                create table if not exists auth_identity (
                  id uuid primary key, version bigint default 0 not null,
                  created_at timestamp with time zone not null, updated_at timestamp with time zone not null,
                  created_by uuid, updated_by uuid,
                  user_id uuid not null references user_account(id), provider varchar(20) not null,
                  subject varchar(320) not null, label varchar(200), last_used_at timestamp with time zone,
                  constraint uq_auth_identity_provider_subject unique (provider, subject))""");
        jdbc.execute("""
                create table if not exists email_blocklist (
                  id uuid primary key, version bigint default 0 not null,
                  created_at timestamp with time zone not null, updated_at timestamp with time zone not null,
                  created_by uuid, updated_by uuid, pattern varchar(320) not null, reason varchar(500))""");
        run(dataSource, "schema,seed");

        assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("select count(*) from auth_identity where provider = 'EMAIL'", Integer.class))
                .isEqualTo(14);
        assertThat(jdbc.queryForList("select subject from auth_identity where provider = 'GOOGLE' order by subject",
                String.class)).containsExactly("fake-google-bot", "fake-google-foodsaver");
        assertThat(jdbc.queryForList("select pattern from email_blocklist order by pattern", String.class))
                .containsExactly("example.invalid", "mailinator.com");
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
