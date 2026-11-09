package network.foodsharing.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The persona panel of the test environment exists only with the {@code staging} profile and a non-blank
 * {@code app.staging.persona-panel.password} (decision 173). Without it there are no persona beans, so
 * {@code /api/personas/**} answers 404.
 */
public class PersonaPanelCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return enabled(context.getEnvironment());
    }

    public static boolean enabled(Environment environment) {
        String password = environment.getProperty(StagingProfileGuard.PANEL_PASSWORD, "");
        return environment.acceptsProfiles(Profiles.of(StagingProfileGuard.STAGING)) && !password.isBlank();
    }
}
