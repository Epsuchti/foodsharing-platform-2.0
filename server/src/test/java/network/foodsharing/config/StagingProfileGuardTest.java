package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Decision 173: staging only as {@code prod,staging} (test suite: {@code test,staging}), panel password only there. */
class StagingProfileGuardTest {

    @Test
    void allowsProdAloneLocalE2eAndProdBeforeStaging() {
        assertThatCode(() -> StagingProfileGuard.check(List.of("prod"), "")).doesNotThrowAnyException();
        assertThatCode(() -> StagingProfileGuard.check(List.of("local"), null)).doesNotThrowAnyException();
        assertThatCode(() -> StagingProfileGuard.check(List.of("e2e"), "")).doesNotThrowAnyException();
        assertThatCode(() -> StagingProfileGuard.check(List.of(), "")).doesNotThrowAnyException();
        assertThatCode(() -> StagingProfileGuard.check(List.of("prod", "staging"), "foobar"))
                .doesNotThrowAnyException();
        assertThatCode(() -> StagingProfileGuard.check(List.of("prod", "staging"), "")).doesNotThrowAnyException();
        // the test suite's base profile stands in for prod, still before staging
        assertThatCode(() -> StagingProfileGuard.check(List.of("test", "staging"), "foobar"))
                .doesNotThrowAnyException();
    }

    @Test
    void stagingNeedsProdFirst() {
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("staging"), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs the prod profile");
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("staging", "prod"), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prod profile must be listed before");
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("staging", "test"), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("test profile must be listed before");
    }

    @Test
    void stagingNeverWithLocalOrE2e() {
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("prod", "staging", "local"), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("local or e2e");
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("e2e", "prod", "staging"), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("local or e2e");
    }

    @Test
    void guardStopsTheContextBeforeAnyBeanIsCreated() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("staging", "prod"))
                .withBean(StagingProfileGuard.class)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("prod profile must be listed before staging"));
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "staging"))
                .withPropertyValues("app.staging.persona-panel.password=foobar")
                .withBean(StagingProfileGuard.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void panelPasswordOnlyWithStaging() {
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("prod"), "foobar"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("only allowed with the staging profile");
        assertThatThrownBy(() -> StagingProfileGuard.check(List.of("local"), "foobar"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("only allowed with the staging profile");
    }
}
