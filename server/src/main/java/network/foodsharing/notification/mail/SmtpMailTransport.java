package network.foodsharing.notification.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends mails via Spring Mail (ProtonMail SMTP in prod, Mailpit locally). Active when app.mail.transport=smtp. */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final ObjectProvider<JavaMailSender> mailSender;

    public SmtpMailTransport(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(OutgoingMail mail) {
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
