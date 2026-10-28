package network.foodsharing.mailbox.mapper;

import java.util.List;
import network.foodsharing.api.model.Mail;
import network.foodsharing.api.model.MailAddress;
import network.foodsharing.api.model.MailAttachment;
import network.foodsharing.api.model.MailFolder;
import network.foodsharing.api.model.MailSummary;
import network.foodsharing.api.model.Mailbox;
import network.foodsharing.api.model.MailboxOwnerType;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.service.MailAddresses;
import org.springframework.stereotype.Component;

/** Mailbox domain → API models (A35). */
@Component
public class MailboxMapper {

    public Mailbox toApi(network.foodsharing.mailbox.domain.Mailbox mailbox, String address, int unreadCount,
                         boolean mayManage) {
        return new Mailbox().id(mailbox.getId()).name(mailbox.getName()).emailName(mailbox.getEmailName())
                .address(address).ownerType(MailboxOwnerType.valueOf(mailbox.getOwnerType().name()))
                .ownerId(mailbox.getOwnerId()).unreadCount(unreadCount).mayManage(mayManage);
    }

    public network.foodsharing.api.model.AdminMailbox toAdmin(
            network.foodsharing.mailbox.service.MailboxService.AdminMailboxRow row) {
        var mailbox = row.mailbox();
        return new network.foodsharing.api.model.AdminMailbox().id(mailbox.getId()).name(mailbox.getName())
                .emailName(mailbox.getEmailName()).address(row.address())
                .ownerType(MailboxOwnerType.valueOf(mailbox.getOwnerType().name())).ownerId(mailbox.getOwnerId())
                .ownerName(row.ownerName()).mailCount(row.mailCount());
    }

    public MailSummary toSummary(MailMessage mail) {
        return new MailSummary().id(mail.getId()).mailboxId(mail.getMailboxId())
                .folder(MailFolder.valueOf(mail.getFolder().name()))
                .from(new MailAddress().address(mail.getFromAddress()).name(mail.getFromName()))
                .to(addresses(mail.getToJson())).subject(mail.getSubject())
                .receivedAt(TimeMapper.toOffset(mail.getReceivedAt())).read(mail.getReadAt() != null)
                .answered(mail.getAnsweredAt() != null).bounce(mail.isBounce())
                .hasAttachments(mail.getAttachmentCount() > 0).attachmentCount(mail.getAttachmentCount());
    }

    public Mail toDetail(MailMessage mail, List<network.foodsharing.mailbox.domain.MailAttachment> attachments) {
        Mail model = new Mail().id(mail.getId()).mailboxId(mail.getMailboxId())
                .folder(MailFolder.valueOf(mail.getFolder().name()))
                .from(new MailAddress().address(mail.getFromAddress()).name(mail.getFromName()))
                .to(addresses(mail.getToJson())).cc(addresses(mail.getCcJson())).subject(mail.getSubject())
                .receivedAt(TimeMapper.toOffset(mail.getReceivedAt())).read(mail.getReadAt() != null)
                .answered(mail.getAnsweredAt() != null).bounce(mail.isBounce())
                .hasAttachments(!attachments.isEmpty()).attachmentCount(attachments.size())
                .bodyText(mail.getBodyText()).bodyHtml(mail.getBodyHtml()).inReplyTo(mail.getInReplyTo())
                .messageId(mail.getMessageId())
                .attachments(attachments.stream().map(a -> toApi(mail, a)).toList());
        if (mail.getReplyTo() != null) {
            model.replyTo(new MailAddress().address(mail.getReplyTo()));
        }
        return model;
    }

    public MailAttachment toApi(MailMessage mail, network.foodsharing.mailbox.domain.MailAttachment attachment) {
        return new MailAttachment().id(attachment.getId()).fileName(attachment.getFileName())
                .mimeType(attachment.getMimeType()).size(attachment.getSize())
                .url(attachmentUrl(mail.getId().toString(), attachment.getId().toString()));
    }

    public static String attachmentUrl(String mailId, String attachmentId) {
        return "/api/mailboxes/mails/" + mailId + "/attachments/" + attachmentId;
    }

    private static List<MailAddress> addresses(String json) {
        return MailAddresses.fromJson(json).stream()
                .map(a -> new MailAddress().address(a.address()).name(a.name())).toList();
    }
}
