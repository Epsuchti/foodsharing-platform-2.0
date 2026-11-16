package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxMemberRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MB6 (API only, no UI lists mailboxes for deletion): only ORGA deletes a mailbox; one with mails only with
 * {@code force}, which removes the mails and their attachments; a personal mailbox is unlinked from its owner.
 */
class MailboxDeleteTest {

    private final MailboxRepository mailboxes = mock(MailboxRepository.class);
    private final MailboxMemberRepository members = mock(MailboxMemberRepository.class);
    private final MailMessageRepository mails = mock(MailMessageRepository.class);
    private final MailAttachmentRepository attachments = mock(MailAttachmentRepository.class);
    private final MailboxAccessService access = mock(MailboxAccessService.class);
    private final MailAttachmentStore store = mock(MailAttachmentStore.class);
    private final UserService users = mock(UserService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final MailboxService service = new MailboxService(mailboxes, members, mails, attachments, access, store,
            mock(RegionService.class), users, permissions, history, new AppProperties());

    private final SessionPrincipal orga = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.ORGA,
            "orga@test.local");
    private final Mailbox mailbox = new Mailbox();
    private final MailMessage mail = new MailMessage();

    @BeforeEach
    void setUp() {
        mailbox.setId(UUID.randomUUID());
        mailbox.setOwnerType(MailboxOwnerType.USER);
        mailbox.setOwnerId(UUID.randomUUID());
        mailbox.setEmailName("priya.sharma");
        mail.setId(UUID.randomUUID());
        when(access.require(mailbox.getId())).thenReturn(mailbox);
        when(mails.countByMailboxId(mailbox.getId())).thenReturn(1L);
        when(mails.findByMailboxId(mailbox.getId())).thenReturn(List.of(mail));
        when(attachments.findByMailIdOrderByCreatedAtAscIdAsc(mail.getId())).thenReturn(List.of(new MailAttachment()));
        when(history.event(any(HistoryAction.class), any())).thenAnswer(call -> HistoryEvent.builder(
                call.getArgument(0), call.getArgument(1)));
    }

    @Test
    void onlyOrgaDeletesMailboxes() {
        SessionPrincipal ambassador = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR,
                "bot@test.local");
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions).requireOrga(ambassador);
        assertThatThrownBy(() -> service.delete(ambassador, mailbox.getId(), true)).isInstanceOf(ApiException.class);
        verify(mailboxes, never()).delete(any());
    }

    @Test
    void aMailboxWithMailsNeedsForce() {
        assertThatThrownBy(() -> service.delete(orga, mailbox.getId(), false)).isInstanceOfSatisfying(
                ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.MAILBOX_NOT_EMPTY));
        verify(mailboxes, never()).delete(any());
        verify(mails, never()).delete(any());
    }

    @Test
    void forceRemovesMailsAttachmentsMembersAndTheOwnersLink() {
        service.delete(orga, mailbox.getId(), true);
        verify(store).deleteAll(any());
        verify(mails).delete(mail);
        verify(members).deleteAll(any());
        verify(users).setMailboxId(mailbox.getOwnerId(), null);
        verify(mailboxes).delete(mailbox);
    }
}
