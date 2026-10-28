package network.foodsharing.notification.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends mails via Spring Mail (ProtonMail SMTP in prod, Mailpit locally). Active when app.mail.transport=smtp. */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final IntegrationRegistry integrations;

    public SmtpMailTransport(ObjectProvider<JavaMailSender> mailSender, IntegrationRegistry integrations) {
        this.mailSender = mailSender;
        this.integrations = integrations;
        integrations.registerProbe(Integration.SMTP, this::probe);
    }

    @Override
    public void send(OutgoingMail mail) {
        integrations.run(Integration.SMTP, "smtp-send", () -> doSend(mail));
    }

    /** A26 probe: SMTP connect + login. */
    private boolean probe() {
        if (mailSender.getIfAvailable() instanceof JavaMailSenderImpl impl) {
            try {
                impl.testConnection();
                return true;
            } catch (MessagingException ex) {
                return false;
            }
        }
        return false;
    }

    private void doSend(OutgoingMail mail) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No JavaMailSender configured (spring.mail.host)");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            boolean multipart = mail.htmlBody() != null;
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            helper.setFrom(mail.from());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            for (java.util.Map.Entry<String, String> header : mail.headers().entrySet()) {
                message.setHeader(header.getKey(), header.getValue());
            }
            if (multipart) {
                helper.setText(mail.textBody(), mail.htmlBody());
            } else {
                helper.setText(mail.textBody(), false);
            }
            sender.send(message);
        } catch (MessagingException ex) {
            throw new MailSendException("Could not build mail", ex);
        }
    }
}
