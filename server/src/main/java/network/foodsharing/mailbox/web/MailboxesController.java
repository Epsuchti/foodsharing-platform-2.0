package network.foodsharing.mailbox.web;

import network.foodsharing.api.MailboxesApi;
import network.foodsharing.api.model.Mail;
import network.foodsharing.api.model.MailCompose;
import network.foodsharing.api.model.MailFolder;
import network.foodsharing.api.model.MailPage;
import network.foodsharing.api.model.MailSummary;
import network.foodsharing.api.model.MailUpdate;
import network.foodsharing.api.model.Mailbox;
import network.foodsharing.api.model.MailboxCreate;
import network.foodsharing.api.model.MailboxMembersUpdate;
import network.foodsharing.api.model.MailboxUnreadCount;
import network.foodsharing.api.model.MailboxUpdate;
import network.foodsharing.api.model.RegionMailbox;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/** Mailboxes (MB1-MB6). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class MailboxesController implements MailboxesApi {

    @Override
    public ResponseEntity<Mailbox> createRegionMailbox(UUID regionId, MailboxCreate mailboxCreate) {
        throw ApiException.notImplemented("createRegionMailbox");
    }

    @Override
    public ResponseEntity<Mailbox> createUserMailbox(UUID userId, MailboxCreate mailboxCreate) {
        throw ApiException.notImplemented("createUserMailbox");
    }

    @Override
    public ResponseEntity<Void> deleteMail(UUID mailId) {
        throw ApiException.notImplemented("deleteMail");
    }

    @Override
    public ResponseEntity<Void> deleteMailbox(UUID mailboxId, Boolean force) {
        throw ApiException.notImplemented("deleteMailbox");
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> downloadMailAttachment(UUID mailId, UUID attachmentId) {
        throw ApiException.notImplemented("downloadMailAttachment");
    }

    @Override
    public ResponseEntity<Mail> getMail(UUID mailId, Boolean keepUnread) {
        throw ApiException.notImplemented("getMail");
    }

    @Override
    public ResponseEntity<MailboxUnreadCount> getMailboxUnreadCount() {
        throw ApiException.notImplemented("getMailboxUnreadCount");
    }

    @Override
    public ResponseEntity<List<UserSummary>> listMailboxMembers(UUID mailboxId) {
        throw ApiException.notImplemented("listMailboxMembers");
    }

    @Override
    public ResponseEntity<List<Mailbox>> listMailboxes() {
        throw ApiException.notImplemented("listMailboxes");
    }

    @Override
    public ResponseEntity<MailPage> listMails(UUID mailboxId, MailFolder folder, Integer page, Integer size, String q) {
        throw ApiException.notImplemented("listMails");
    }

    @Override
    public ResponseEntity<List<RegionMailbox>> listRegionMailboxes() {
        throw ApiException.notImplemented("listRegionMailboxes");
    }

    @Override
    public ResponseEntity<MailSummary> moveOrFlagMail(UUID mailId, MailUpdate mailUpdate) {
        throw ApiException.notImplemented("moveOrFlagMail");
    }

    @Override
    public ResponseEntity<Mail> sendMail(UUID mailboxId, MailCompose mailCompose) {
        throw ApiException.notImplemented("sendMail");
    }

    @Override
    public ResponseEntity<List<UserSummary>> setMailboxMembers(UUID mailboxId, MailboxMembersUpdate mailboxMembersUpdate) {
        throw ApiException.notImplemented("setMailboxMembers");
    }

    @Override
    public ResponseEntity<Mailbox> updateMailbox(UUID mailboxId, MailboxUpdate mailboxUpdate) {
        throw ApiException.notImplemented("updateMailbox");
    }
}
