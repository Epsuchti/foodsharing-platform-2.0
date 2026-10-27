package network.foodsharing.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Backend texts (mail subjects, server-rendered pages) live per module in
 * {@code i18n/<module>/messages[_<lang>].properties}; every module folder is discovered automatically, so modules
 * never edit a shared file.
 */
@Configuration
public class MessageSourceConfig {

    static final String PATTERN = "classpath*:i18n/*/messages*.properties";

    @Bean
    public MessageSource messageSource() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PATTERN);
        TreeSet<String> basenames = new TreeSet<>();
        Arrays.stream(resources)
                .map(MessageSourceConfig::basename)
                .filter(Objects::nonNull)
                .forEach(basenames::add);
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(basenames.toArray(String[]::new));
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    static String basename(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int index = url.lastIndexOf("/i18n/");
            if (index < 0) {
                return null;
            }
            String relative = url.substring(index + 1); // i18n/<module>/messages_xx.properties
            String[] parts = relative.split("/");
            if (parts.length != 3) {
                return null;
            }
            return "classpath:i18n/" + parts[1] + "/messages";
        } catch (IOException ex) {
            return null;
        }
    }
}
