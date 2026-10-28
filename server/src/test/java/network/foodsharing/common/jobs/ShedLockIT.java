package network.foodsharing.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.testsupport.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** A25: two parallel scheduled runs of the same job on PostgreSQL → only one executes; runNow() takes no lock. */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, ShedLockIT.Config.class})
class ShedLockIT {

    @Autowired
    private SlowJob job;

    @Autowired
    private JobRunner runner;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwoParallelRunsExecutes() throws Exception {
        CompletableFuture<Void> first = CompletableFuture.runAsync(job::run);
        assertThat(job.started().await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = CompletableFuture.runAsync(job::run);
        second.get(10, TimeUnit.SECONDS);
        job.release().countDown();
        first.get(10, TimeUnit.SECONDS);

        assertThat(job.executions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from job_run where job_name = ? and outcome = 'OK'",
                Integer.class, SlowJob.NAME)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from shedlock where name = ?", Integer.class, SlowJob.NAME))
                .isEqualTo(1);
        assertThat(runner.lastRun(SlowJob.NAME)).get().extracting(JobRun::getTrigger).isEqualTo(JobRun.Trigger.SCHEDULE);

        // runNow() (tests, ORGA "run now") is re-entrant and bypasses the lock.
        job.runNow();
        assertThat(job.executions()).isEqualTo(2);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        SlowJob slowJob(JobRunner runner) {
            return new SlowJob(runner);
        }
    }

    static class SlowJob implements RunnableJob {
        static final String NAME = "shedlock-it";

        final AtomicInteger executions = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final JobRunner runner;

        SlowJob(JobRunner runner) {
            this.runner = runner;
        }

        @SchedulerLock(name = NAME, lockAtLeastFor = "PT0S")
        public void run() {
            runner.run(this, JobRun.Trigger.SCHEDULE, null);
        }

        // Accessors: the bean is a CGLIB proxy (ShedLock), its fields are not the target's.
        public CountDownLatch started() {
            return started;
        }

        public CountDownLatch release() {
            return release;
        }

        public int executions() {
            return executions.get();
        }

        @Override
        public String jobName() {
            return NAME;
        }

        @Override
        public void runNow() {
            executions.incrementAndGet();
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
