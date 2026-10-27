package network.foodsharing.config;

import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class VersionInfoContributor implements InfoContributor {

    private final AppVersionResolver appVersionResolver;

    public VersionInfoContributor(AppVersionResolver appVersionResolver) {
        this.appVersionResolver = appVersionResolver;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("app", Map.of("version", appVersionResolver.resolve()));
    }
}
