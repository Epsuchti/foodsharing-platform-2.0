package network.foodsharing.mailbox.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import org.junit.jupiter.api.Test;

/** A35: deleting a mail removes its uploads unless a forwarded copy still references them; attach claims. */
class MailAttachmentStoreTest {

    private final MailAttachmentRepository attachments = mock(MailAttachmentRepository.class);
    private final UploadService uploads = mock(UploadService.class);
    private final MailAttachmentStore store = new MailAttachmentStore(attachments, uploads);

    @Test
    void sharedUploadsSurviveUntilTheirLastAttachment() {
        UUID shared = UUID.randomUUID();
        UUID own = UUID.randomUUID();
        Upload ownUpload = new Upload();
        when(attachments.countByUploadId(shared)).thenReturn(1L);
        when(attachments.countByUploadId(own)).thenReturn(0L);
        when(uploads.find(own)).thenReturn(ownUpload);
        store.deleteAll(List.of(attachment(shared), attachment(own)));
        verify(uploads).delete(ownUpload);
        verify(uploads, never()).find(shared);
    }

    @Test
    void attachingClaimsTheUploadForTheMail() {
        UUID mailId = UUID.randomUUID();
        Upload upload = new Upload();
        upload.setFileName("a.pdf");
        upload.setMimeType("application/pdf");
        when(attachments.save(any())).thenAnswer(i -> i.getArgument(0));
        store.attach(mailId, upload, "cid1");
        verify(uploads).claim(upload.getId(), new UploadUsage(MailAttachmentStore.MODULE, mailId));
    }

    private static MailAttachment attachment(UUID uploadId) {
        MailAttachment attachment = new MailAttachment();
        attachment.setUploadId(uploadId);
        return attachment;
    }
}
