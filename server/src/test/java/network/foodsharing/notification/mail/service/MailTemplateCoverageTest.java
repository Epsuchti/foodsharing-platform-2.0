package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import network.foodsharing.api.model.Language;
import network.foodsharing.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * A27: every notification type that sends a mail fragment ({@code templates/mail/notification/<TYPE>}) has 10 files
 * (html + txt in five languages) and a subject in every language; every fragment belongs to a real type.
 */
class MailTemplateCoverageTest {

    /** M4 types with a mail (docs/plan-m4.md B3, B5: NEWSLETTER); TELEGRAM_LINKED has none. */
    static final Set<NotificationType> MAILED = Set.of(NotificationType.PICKUP_AUTO_DECLINED,
            NotificationType.PICKUP_AUTO_CONFIRMED_WITH_MESSAGE, NotificationType.STORE_REQUEST_AUTO_DECLINED,
            NotificationType.STORE_REQUEST_AUTO_ACCEPTED, NotificationType.PICKUP_LATE_CANCELLATION,
            NotificationType.PICKUP_SIGNOUT_NOT_REPLACED, NotificationType.INTEGRATION_DOWN,
            NotificationType.NEWSLETTER, NotificationType.FORUM_POST, NotificationType.FORUM_THREAD_TO_ACTIVATE);

    @Test
    void everyMailedTypeHasTenFilesAndFiveSubjects() throws IOException {
        List<String> missing = new ArrayList<>();
        for (NotificationType type : MAILED) {
            for (Language language : Language.values()) {
                for (String extension : List.of("html", "txt")) {
                    String file = "templates/mail/notification/" + type.name() + "_" + language.getValue() + "."
                            + extension;
                    if (!new ClassPathResource(file).exists()) {
                        missing.add(file);
                    }
                }
                Properties subjects = new Properties();
                try (InputStream in = new ClassPathResource("i18n/mail/messages_" + language.getValue() + ".properties")
                        .getInputStream()) {
                    subjects.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                }
                if (!subjects.containsKey("mail.notification/" + type.name() + ".subject")) {
                    missing.add("subject " + type + " " + language.getValue());
                }
            }
        }
        assertThat(missing).isEmpty();
        assertThat(Language.values()).hasSize(5);
    }

    @Test
    void everyFragmentBelongsToAType() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:templates/mail/notification/*.*");
        assertThat(files).hasSize(MAILED.size() * 10);
        for (Resource file : files) {
            String type = file.getFilename().substring(0, file.getFilename().lastIndexOf('_'));
            assertThat(NotificationType.valueOf(type)).isIn(MAILED);
        }
    }
}
