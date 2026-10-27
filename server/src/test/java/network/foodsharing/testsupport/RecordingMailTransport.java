package network.foodsharing.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import network.foodsharing.notification.mail.MailTransport;
import network.foodsharing.notification.mail.OutgoingMail;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test {@link MailTransport} that records mails (optionally failing) instead of sending them. */
public class RecordingMailTransport implements MailTransport {

    private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    @Override
    public void send(OutgoingMail mail) {
        if (failure != null) {
            throw failure;
        }
        sent.add(mail);
    }

    public List<OutgoingMail> sent() {
        return List.copyOf(sent);
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void clear() {
        sent.clear();
        failure = null;
    }

    /** First {@code token=…} value of a link in the text body. */
    public static String token(OutgoingMail mail) {
        Matcher matcher = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)").matcher(mail.textBody());
        if (!matcher.find()) {
            throw new AssertionError("No token link in mail: " + mail.textBody());
        }
        return matcher.group(1);
    }

    /** Registers the recording transport as the primary {@link MailTransport}. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingMailTransport recordingMailTransport() {
            return new RecordingMailTransport();
        }
    }
}
