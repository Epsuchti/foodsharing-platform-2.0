package network.foodsharing.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast check of the test environment's profile combination (decision 173), before any bean (Liquibase
 * included) is created:
 * <ul>
 *   <li>{@code staging} only on top of its base profile, listed before it: {@code prod,staging} on the server (later
 *       profiles win, so staging's {@code spring.liquibase.contexts=schema,seed} overrides prod's {@code schema}).
 *       The test suite uses {@code test} as the base ({@code test,staging}: H2 instead of prod's environment
 *       variables); either way the base must come first.</li>
 *   <li>{@code staging} never together with {@code local} or {@code e2e} (the dev API must not exist there).</li>
 *   <li>a persona panel password only with {@code staging} (prod alone never has one).</li>
 * </ul>
 */
@Component
public class StagingProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    public static final String STAGING = "staging";
    static final String PANEL_PASSWORD = "app.staging.persona-panel.password";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        check(Arrays.asList(environment.getActiveProfiles()), environment.getProperty(PANEL_PASSWORD, ""));
    }

    /** @throws IllegalStateException when the combination is not allowed */
    public static void check(List<String> activeProfiles, String panelPassword) {
        List<String> problems = new ArrayList<>();
        int staging = activeProfiles.indexOf(STAGING);
        if (staging >= 0) {
            if (activeProfiles.contains("local") || activeProfiles.contains("e2e")) {
                problems.add("the staging profile must not be combined with local or e2e");
            }
            String base = activeProfiles.contains("test") ? "test" : "prod";
            int baseIndex = activeProfiles.indexOf(base);
            if (baseIndex < 0) {
                problems.add("the staging profile needs the prod profile (SPRING_PROFILES_ACTIVE=prod,staging)");
            } else if (baseIndex > staging) {
                problems.add("the " + base + " profile must be listed before staging (SPRING_PROFILES_ACTIVE=prod,staging)");
            }
        } else if (panelPassword != null && !panelPassword.isBlank()) {
            problems.add(PANEL_PASSWORD + " (PERSONA_PANEL_PASSWORD) is only allowed with the staging profile");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid profiles " + activeProfiles + ": " + String.join("; ", problems));
        }
    }
}
