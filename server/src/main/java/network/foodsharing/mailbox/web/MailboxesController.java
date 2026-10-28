package network.foodsharing.mailbox.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.mapper.MailboxMapper;
import network.foodsharing.mailbox.service.MailService;
import network.foodsharing.mailbox.service.MailboxAccessService;
import network.foodsharing.mailbox.service.MailboxDirectory;
import network.foodsharing.mailbox.service.MailboxService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Mailboxes (MB1–MB4, MB6; docs/conventions.md A35). */
@RestController
public class MailboxesController implements MailboxesApi {

    private final MailboxService mailboxes;
    private final MailService mails;
    private final MailboxAccessService access;
    private final MailboxDirectory directory;
    private final CurrentActorService currentActor;
    private final MailboxMapper mapper;
    private final UserMapper userMapper;

    public MailboxesController(MailboxService mailboxes, MailService mails, MailboxAccessService access,
                               MailboxDirectory directory, CurrentActorService currentActor, MailboxMapper mapper,
                               UserMapper userMapper) {
        this.mailboxes = mailboxes;
        this.mails = mails;
        this.access = access;
        this.directory = directory;
        this.currentActor = currentActor;
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Override
    public ResponseEntity<List<Mailbox>> listMailboxes() {
        SessionPrincipal me = currentActor.requirePrincipal();
        List<network.foodsharing.mailbox.domain.Mailbox> visible = access.visibleMailboxes(me);
        Map<UUID, Integer> unread = mails.unreadCounts(visible.stream().map(m -> m.getId()).toList());
        return ResponseEntity.ok(visible.stream().map(m -> toApi(me, m, unread.getOrDefault(m.getId(), 0))).toList());
    }

    @Override
    public ResponseEntity<MailboxUnreadCount> getMailboxUnreadCount() {
        return ResponseEntity.ok(new MailboxUnreadCount().count(mails.totalUnread(currentActor.requirePrincipal())));
    }

    @Override
    public ResponseEntity<Mailbox> createRegionMailbox(UUID regionId, MailboxCreate create) {
        SessionPrincipal me = currentActor.requirePrincipal();
        var mailbox = mailboxes.createForRegion(me, regionId, create.getEmailName(), create.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(me, mailbox, 0));
    }

    @Override
    public ResponseEntity<List<RegionMailbox>> listRegionMailboxes() {
        currentActor.requirePrincipal();
        return ResponseEntity.ok(directory.regionMailboxes().stream()
                .map(e -> new RegionMailbox().regionId(e.regionId()).regionName(e.regionName()).address(e.address()))
                .sorted(Comparator.comparing(RegionMailbox::getRegionName, String.CASE_INSENSITIVE_ORDER)).toList());
    }

    @Override
    public ResponseEntity<Mailbox> createUserMailbox(UUID userId, MailboxCreate create) {
        SessionPrincipal me = currentActor.requirePrincipal();
        var mailbox = mailboxes.createForUser(me, userId, create.getEmailName(), create.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(me, mailbox, 0));
    }

    @Override
    public ResponseEntity<Mailbox> updateMailbox(UUID mailboxId, MailboxUpdate update) {
        SessionPrincipal me = currentActor.requirePrincipal();
        var mailbox = mailboxes.update(me, mailboxId, update.getEmailName(), update.getName());
        return ResponseEntity.ok(toApi(me, mailbox, mails.unreadCounts(List.of(mailboxId)).get(mailboxId)));
    }

    @Override
    public ResponseEntity<Void> deleteMailbox(UUID mailboxId, Boolean force) {
        mailboxes.delete(currentActor.requirePrincipal(), mailboxId, Boolean.TRUE.equals(force));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<UserSummary>> listMailboxMembers(UUID mailboxId) {
        return ResponseEntity.ok(mailboxes.members(currentActor.requirePrincipal(), mailboxId).stream()
                .map(userMapper::toSummary).toList());
    }

    @Override
    public ResponseEntity<List<UserSummary>> setMailboxMembers(UUID mailboxId, MailboxMembersUpdate update) {
        return ResponseEntity.ok(mailboxes.setMembers(currentActor.requirePrincipal(), mailboxId, update.getUserIds())
                .stream().map(userMapper::toSummary).toList());
    }

    @Override
    public ResponseEntity<MailPage> listMails(UUID mailboxId, MailFolder folder, Integer page, Integer size, String q) {
        Page<MailMessage> result = mails.page(currentActor.requirePrincipal(), mailboxId,
                network.foodsharing.mailbox.domain.MailFolder.valueOf(folder.name()), page == null ? 0 : page,
                size == null ? 20 : size, q);
        return ResponseEntity.ok(new MailPage().items(result.getContent().stream().map(mapper::toSummary).toList())
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<Mail> getMail(UUID mailId, Boolean keepUnread) {
        MailMessage mail = mails.open(currentActor.requirePrincipal(), mailId, Boolean.TRUE.equals(keepUnread));
        return ResponseEntity.ok(mapper.toDetail(mail, mails.attachmentsOf(mailId)));
    }

    @Override
    public ResponseEntity<MailSummary> moveOrFlagMail(UUID mailId, MailUpdate update) {
        MailMessage mail = mails.update(currentActor.requirePrincipal(), mailId, update.getRead(),
                update.getFolder() == null ? null
                        : network.foodsharing.mailbox.domain.MailFolder.valueOf(update.getFolder().name()));
        return ResponseEntity.ok(mapper.toSummary(mail));
    }

    @Override
    public ResponseEntity<Void> deleteMail(UUID mailId) {
        mails.delete(currentActor.requirePrincipal(), mailId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Mail> sendMail(UUID mailboxId, MailCompose compose) {
        MailMessage sent = mails.send(currentActor.requirePrincipal(), mailboxId, new MailService.Compose(
                compose.getTo(), compose.getCc(), compose.getBcc(), compose.getSubject(), compose.getBodyHtml(),
                compose.getAttachmentUploadIds(), compose.getInReplyToMailId(), compose.getForwardOfMailId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDetail(sent, mails.attachmentsOf(sent.getId())));
    }

    @Override
    public ResponseEntity<Resource> downloadMailAttachment(UUID mailId, UUID attachmentId) {
        MailAttachment attachment = mails.requireAttachment(currentActor.requirePrincipal(), mailId, attachmentId);
        Upload upload = directory.upload(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .contentLength(upload.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(directory.open(upload)));
    }

    private Mailbox toApi(SessionPrincipal me, network.foodsharing.mailbox.domain.Mailbox mailbox, int unread) {
        return mapper.toApi(mailbox, mailboxes.address(mailbox), unread, access.mayManage(me, mailbox));
    }
}
