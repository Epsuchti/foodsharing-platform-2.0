package network.foodsharing.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ShedLock (A25): every {@code *Job.run()} carries {@code @SchedulerLock(name = <jobName()>)}, so with several
 * instances only one runs a job at a time. Active in every profile (also when scheduling is off), so tests can call
 * {@code run()} concurrently; {@code runNow()} takes no lock.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "${app.jobs.default-lock-at-most-for:PT30M}")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("shedlock")
                .build());
    }
}
