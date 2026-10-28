package network.foodsharing.mailbox.service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mail attachments in the upload store (A34/A35): uploads are claimed with usage {@code mailbox/<mailId>}; an upload
 * shared by several mails (forward) is deleted only with its last attachment row.
 */
@Component
public class MailAttachmentStore {

    public static final String MODULE = "mailbox";

    private final MailAttachmentRepository attachments;
    private final UploadService uploads;

    public MailAttachmentStore(MailAttachmentRepository attachments, UploadService uploads) {
        this.attachments = attachments;
        this.uploads = uploads;
    }

    /** Claims the upload for the mail and stores the attachment row. */
    @Transactional
    @NoHistory(reason = "UploadService.claim records UploadAction.CLAIMED; the mail action is recorded by the caller")
    public MailAttachment attach(UUID mailId, Upload upload, String contentId) {
        uploads.claim(upload.getId(), new UploadUsage(MODULE, mailId));
        MailAttachment attachment = new MailAttachment();
        attachment.setMailId(mailId);
        attachment.setUploadId(upload.getId());
        attachment.setFileName(upload.getFileName());
        attachment.setMimeType(upload.getMimeType());
        attachment.setSize(upload.getSize());
        attachment.setContentId(contentId);
        return attachments.save(attachment);
    }

    /** Removes the attachment rows and every upload no other mail references any more. */
    @Transactional
    @NoHistory(reason = "UploadService.delete records UploadAction.DELETED; the mail action is recorded by the caller")
    public void deleteAll(List<MailAttachment> files) {
        attachments.deleteAll(files);
        attachments.flush();
        for (MailAttachment file : files) {
            if (attachments.countByUploadId(file.getUploadId()) == 0) {
                uploads.delete(uploads.find(file.getUploadId()));
            }
        }
    }

    public Upload upload(MailAttachment attachment) {
        return uploads.find(attachment.getUploadId());
    }

    public InputStream open(MailAttachment attachment) {
        return uploads.open(upload(attachment));
    }
}
