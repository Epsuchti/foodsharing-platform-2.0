package network.foodsharing.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import network.foodsharing.api.model.AppInfo;
import network.foodsharing.common.web.PublicController;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.AppVersionResolver;
import network.foodsharing.config.PersonaPanelCondition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

/**
 * Decision 173: the persona panel needs the staging profile and a non-blank password. Staging without a password has
 * no persona beans (so {@code /api/personas/**} has no handler and answers 404, as {@code ProdLikeProfileTest} shows
 * for the no-bean case) and app-info reports the banner but no panel. The panel itself: {@code PersonaPanelStagingTest}.
 */
class PersonaPanelConditionTest {

    private static final String PASSWORD = "app.staging.persona-panel.password";

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("network.foodsharing.persona")
    static class PersonaPackage {
    }

    private static MockEnvironment environment(String password, String... profiles) {
        MockEnvironment environment = new MockEnvironment().withProperty(PASSWORD, password);
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void onlyStagingWithAPasswordEnablesThePanel() {
        assertThat(PersonaPanelCondition.enabled(environment("foobar", "test", "staging"))).isTrue();
        assertThat(PersonaPanelCondition.enabled(environment("", "test", "staging"))).isFalse();
        assertThat(PersonaPanelCondition.enabled(environment("  ", "test", "staging"))).isFalse();
        assertThat(PersonaPanelCondition.enabled(environment("foobar", "test"))).isFalse();
        assertThat(PersonaPanelCondition.enabled(new MockEnvironment())).isFalse();
    }

    @Test
    void stagingWithoutAPasswordHasNoPersonaBeans() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test", "staging"))
                .withPropertyValues(PASSWORD + "=")
                .withUserConfiguration(PersonaPackage.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(Arrays.stream(context.getBeanDefinitionNames())
                            .map(context::getType)
                            .filter(type -> type != null && type.getName().startsWith("network.foodsharing.persona.")
                                    && !type.equals(PersonaPackage.class)))
                            .isEmpty();
                });
        // the scan does reach the persona beans: with a password they are created (and fail here for lack of
        // their repositories)
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test", "staging"))
                .withPropertyValues(PASSWORD + "=foobar")
                .withUserConfiguration(PersonaPackage.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void appInfoHasTheBannerButNoPanelWithoutAPassword() {
        AppProperties properties = new AppProperties();
        properties.setDemoBanner(true);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
        AppInfo info = new PublicController(properties, mock(AppVersionResolver.class), clock,
                environment("", "test", "staging")).getAppInfo().getBody();
        assertThat(info.getPersonaPanel()).isFalse();
        assertThat(info.getDemoBanner()).isTrue();

        AppInfo withPassword = new PublicController(properties, mock(AppVersionResolver.class), clock,
                environment("foobar", "test", "staging")).getAppInfo().getBody();
        assertThat(withPassword.getPersonaPanel()).isTrue();
    }
}
