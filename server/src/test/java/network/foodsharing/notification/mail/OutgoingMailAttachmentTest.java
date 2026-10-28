package network.foodsharing.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;

/** A35: the SMTP transport builds a multipart MIME mail with the mailbox sender, Reply-To, headers and attachments. */
class OutgoingMailAttachmentTest {

    @Test
    void mimeMessageCarriesFromOverrideHeadersAndAttachments() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        IntegrationRegistry integrations = mock(IntegrationRegistry.class);
        doAnswer(i -> {
            ((Runnable) i.getArgument(2)).run();
            return null;
        }).when(integrations).run(eq(Integration.SMTP), any(), any());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("sender", sender);
        SmtpMailTransport transport = new SmtpMailTransport(beans.getBeanProvider(JavaMailSender.class), integrations);

        transport.send(new OutgoingMail("anna@example.ch", "Zürich Stadt <zuerich@local.test>", "Hallo", "Text",
                "<p>Text</p>", Map.of("Message-ID", "<m1@local.test>", "In-Reply-To", "<o@example.ch>"),
                "antwort@local.test", List.of(new OutgoingMail.Attachment("plan.pdf", "application/pdf",
                        () -> "%PDF".getBytes()))));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getFrom()[0].toString()).contains("zuerich@local.test");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("antwort@local.test");
        assertThat(message.getHeader("In-Reply-To")[0]).isEqualTo("<o@example.ch>");
        assertThat(attachmentNames((Multipart) message.getContent())).containsExactly("plan.pdf");
    }

    private static List<String> attachmentNames(Multipart multipart) throws Exception {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                names.add(part.getFileName());
            } else if (part.getContent() instanceof Multipart nested) {
                names.addAll(attachmentNames(nested));
            }
        }
        return names;
    }
}
