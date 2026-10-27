package network.foodsharing.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/** Version from APP_VERSION_FILE (Docker image), else build-info, else the jar manifest. */
@Component
public class AppVersionResolver {

    private final BuildProperties buildProperties;
    private final String versionFilePath;

    public AppVersionResolver(ObjectProvider<BuildProperties> buildPropertiesProvider,
                              @Value("${APP_VERSION_FILE:}") String versionFilePath) {
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
        this.versionFilePath = versionFilePath == null ? "" : versionFilePath.trim();
    }

    public String resolve() {
        String fileVersion = readVersionFile();
        if (fileVersion != null) {
            return fileVersion;
        }
        if (buildProperties != null && buildProperties.getVersion() != null && !buildProperties.getVersion().isBlank()) {
            return buildProperties.getVersion();
        }
        Package appPackage = getClass().getPackage();
        if (appPackage != null && appPackage.getImplementationVersion() != null
                && !appPackage.getImplementationVersion().isBlank()) {
            return appPackage.getImplementationVersion();
        }
        return "unknown";
    }

    private String readVersionFile() {
        if (versionFilePath.isBlank()) {
            return null;
        }
        try {
            String raw = Files.readString(Path.of(versionFilePath)).trim();
            return raw.isBlank() ? null : raw;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
